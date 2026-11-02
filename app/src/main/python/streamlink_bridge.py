"""Chaquopy bridge for the embedded Streamlink runtime and external plugins."""

import ast
import html
import json
import re
from urllib.parse import urlparse


def runtime_status():
    try:
        import streamlink
    except Exception as error:
        return json.dumps({"available": False, "error": str(error)})
    return json.dumps({
        "available": True,
        "version": getattr(streamlink, "__version__", "unknown"),
        "error": "",
    })


def inspect_plugin(source):
    tree = ast.parse(source)
    classes = []
    matchers = []
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef):
            bases = [ast.unparse(base) for base in node.bases]
            if any(base.endswith("Plugin") for base in bases):
                classes.append(node.name)
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id in {"url_re", "url"}:
                    value = ast.unparse(node.value)
                    matchers.append(value)
    source_domains = re.findall(
        r"(?:https?://)?(?:www\.)?([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})",
        source,
    )
    domains = sorted(set(source_domains + re.findall(
        r"(?:www\.)?([a-zA-Z0-9-]+\.[a-zA-Z]{2,})", "\n".join(matchers)
    )))
    return json.dumps({
        "valid": bool(classes),
        "classes": classes,
        "matchers": matchers,
        "domains": domains,
    })


def _direct_hls_fallback(session, input_url):
    """Extract a public HLS URL from pages whose plugin metadata changed."""
    try:
        response = session.http.get(
            input_url,
            headers={"Referer": input_url},
        )
        response.raise_for_status()
        page = html.unescape(response.text).replace("\\/", "/")
    except Exception:
        return None

    candidates = re.findall(
        r"""https?://[^"'<>\s]+?\.m3u8(?:\?[^"'<>\s]*)?""",
        page,
        re.IGNORECASE,
    )
    candidates += re.findall(
        r"""["']contentUrl["']\s*:\s*["']([^"']+\.m3u8(?:\?[^"']*)?)["']""",
        page,
        re.IGNORECASE,
    )
    origin = "{uri.scheme}://{uri.netloc}".format(uri=urlparse(input_url))
    for candidate in dict.fromkeys(candidates):
        return {
            "ok": True,
            "url": candidate.replace("\\u0026", "&"),
            "isHls": True,
            "quality": "best",
            "qualities": ["best"],
            "headers": {
                "Referer": input_url,
                "Origin": origin,
            },
        }
    return None


def resolve(input_url, plugin_directory=None, quality="best"):
    from streamlink import Streamlink
    session = Streamlink()
    session.set_option("http-timeout", 15)

    # Set realistic browser User-Agent so Cerbero / Mediaset APIs don't block Python requests
    session.http.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "es-ES,es;q=0.9",
    })

    # Remove unwanted built-in plugins so user can load them manually if desired
    for name in ["mediasetinfinity"]:
        try:
            if hasattr(session, "plugins"):
                if isinstance(session.plugins, dict):
                    session.plugins.pop(name, None)
                if hasattr(session.plugins, "plugins") and isinstance(session.plugins.plugins, dict):
                    session.plugins.plugins.pop(name, None)
        except Exception:
            pass

    loaded_plugins = []
    try:
        if plugin_directory:
            loader = getattr(session.plugins, "load_path", None)
            if loader is not None:
                loader(plugin_directory)
            else:
                session.load_plugins(plugin_directory)
            loaded_plugins = sorted(session.plugins.get_names())
        resolved_plugin = session.resolve_url(input_url)
        if isinstance(resolved_plugin, tuple):
            plugin_name = next(
                (getattr(item, "__name__", None) for item in resolved_plugin if getattr(item, "__name__", None)),
                "tuple",
            )
        else:
            plugin_name = getattr(resolved_plugin, "__name__", type(resolved_plugin).__name__)
        streams = session.streams(input_url)
        if not streams:
            # A direct HLS URL does not need a site plugin. Keep this fallback
            # for providers whose playlist is already the playable resource.
            if input_url.lower().split("?", 1)[0].endswith(".m3u8"):
                return json.dumps({
                    "ok": True,
                    "url": input_url,
                    "isHls": True,
                    "quality": "best",
                    "qualities": ["best"],
                    "headers": {},
                })
            fallback = _direct_hls_fallback(session, input_url)
            if fallback:
                return json.dumps(fallback)
            return json.dumps({
                "ok": False,
                "errorCode": "STREAMLINK_NO_STREAMS",
                "error": "Streamlink no encontró streams para esta URL. "
                         f"Plugins cargados: {', '.join(loaded_plugins)}",
                "diagnostics": f"plugin={plugin_name}; streams=0",
            })
        selected = streams.get(quality) or streams.get("best") or next(iter(streams.values()))
        playback_url = getattr(selected, "url", None)
        if not playback_url:
            return json.dumps({
                "ok": False,
                "errorCode": "STREAM_REQUIRES_OPEN",
                "error": "El stream requiere stream.open() y no expone una URL reproducible por Media3",
                "diagnostics": f"plugin={plugin_name}; streams={','.join(streams.keys())}",
            })

        is_mediaset = "mediaset" in input_url.lower() or "mitele" in input_url.lower() or "mitele" in plugin_name.lower() or "mitele" in str(resolved_plugin).lower()

        headers = {
            str(name): str(value)
            for name, value in (getattr(selected, "headers", {}) or {}).items()
            if value is not None
        }
        # Streamlink's HLS streams may keep request headers on the stream's
        # session rather than on the selected stream object.
        if not headers:
            headers = {
                str(name): str(value)
                for name, value in (getattr(session.http, "headers", {}) or {}).items()
                if value is not None
            }

        if is_mediaset:
            headers["Origin"] = "https://www.mediaset.es"
            headers["Referer"] = "https://www.mediasetinfinity.es/"
            headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        return json.dumps({
            "ok": True,
            "url": playback_url,
            "isHls": selected.__class__.__name__.lower().endswith("hlsstream"),
            "quality": quality if quality in streams else "best",
            "qualities": list(streams.keys()),
            "headers": dict(headers),
            "diagnostics": (
                f"plugin={plugin_name}; streams={','.join(streams.keys())}; "
                f"selected={selected.__class__.__name__}; headerKeys={','.join(headers.keys())}; isMediaset={is_mediaset}"
            ),
        })
    except Exception as error:
        fallback = _direct_hls_fallback(session, input_url)
        if fallback:
            return json.dumps(fallback)
        return json.dumps({
            "ok": False,
            "errorCode": f"STREAMLINK_{type(error).__name__.upper()}",
            "error": f"{type(error).__name__}: {error}. "
                     f"Plugins cargados: {', '.join(loaded_plugins)}",
        })
