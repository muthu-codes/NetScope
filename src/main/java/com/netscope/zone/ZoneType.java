package com.netscope.zone;

/** What kind of network a zone is. LOCAL = directly connected (Layer 2 visible). Everything else is reached through routers. */
public enum ZoneType { LOCAL, ROUTED, SERVER, MANAGEMENT, CUSTOM }
