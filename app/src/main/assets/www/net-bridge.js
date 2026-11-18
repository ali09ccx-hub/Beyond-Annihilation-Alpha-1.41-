/**
 * After Extinction (ما بعد الفناء) — WebRTC PeerJS Multiplayer Bridge
 * Loaded BEFORE the game script so window.sendNetworkData is registered
 * before the game checks for a native/launcher bridge.
 */
(function (global) {
  'use strict';

  // =========================================================================
  // CONFIG: STUN & Optional TURN Servers
  // =========================================================================
  var NET_CONFIG = {
    peerIdPrefix: 'afterx-',
    joinTimeoutMs: 10000,
    iceServers: [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' }
      // OPTIONAL TURN SERVER (uncomment and fill in for players behind strict NAT):
      // ,{
      //   urls: 'turn:YOUR_TURN_HOST:3478',
      //   username: 'YOUR_TURN_USERNAME',
      //   credential: 'YOUR_TURN_PASSWORD'
      // }
    ],
    // Leave null to use PeerJS public cloud signaling server (0.peerjs.com).
    peerServer: null
  };

  var PEERJS_CDN_URLS = [
    'https://unpkg.com/peerjs@1.5.4/dist/peerjs.min.js',
    'https://cdn.jsdelivr.net/npm/peerjs@1.5.4/dist/peerjs.min.js'
  ];

  var role = null; // 'host' | 'client' | null
  var peer = null;
  var hostConns = new Set();
  var clientConn = null;
  var clientConnected = false;
  var pendingPreOpen = [];
  var joinTimer = null;
  var activeSession = 0;

  function deliver(payload) {
    if (typeof global.onNetworkDataReceived === 'function') {
      global.onNetworkDataReceived(payload);
    }
  }

  function deliverControl(obj) {
    deliver(JSON.stringify(obj));
  }

  function clearJoinTimer() {
    if (joinTimer !== null) {
      clearTimeout(joinTimer);
      joinTimer = null;
    }
  }

  function cleanup() {
    activeSession++;
    clearJoinTimer();
    role = null;
    clientConnected = false;
    pendingPreOpen.length = 0;

    hostConns.forEach(function (conn) {
      try { conn.close(); } catch (_) {}
    });
    hostConns.clear();

    if (clientConn) {
      try { clientConn.close(); } catch (_) {}
      clientConn = null;
    }

    if (peer) {
      try { peer.destroy(); } catch (_) {}
      peer = null;
    }
  }

  function createPeerOptions() {
    var opts = {
      debug: 0,
      config: {
        iceServers: NET_CONFIG.iceServers
      }
    };
    if (NET_CONFIG.peerServer && typeof NET_CONFIG.peerServer === 'object') {
      var ps = NET_CONFIG.peerServer;
      if (ps.host) opts.host = ps.host;
      if (ps.port) opts.port = ps.port;
      if (ps.path) opts.path = ps.path;
      if (typeof ps.secure === 'boolean') opts.secure = ps.secure;
    }
    return opts;
  }

  function ensurePeerJs(onReady, onFail) {
    if (typeof global.Peer === 'function') {
      onReady();
      return;
    }
    var idx = 0;
    function tryLoad() {
      if (typeof global.Peer === 'function') {
        onReady();
        return;
      }
      if (idx >= PEERJS_CDN_URLS.length) {
        onFail();
        return;
      }
      var s = document.createElement('script');
      s.src = PEERJS_CDN_URLS[idx++];
      s.async = true;
      s.onload = function () {
        if (typeof global.Peer === 'function') onReady();
        else tryLoad();
      };
      s.onerror = tryLoad;
      (document.head || document.documentElement).appendChild(s);
    }
    tryLoad();
  }

  function startHost(code) {
    cleanup();
    var mySession = ++activeSession;
    role = 'host';
    var cleanCode = String(code || '').trim().toUpperCase();
    if (!cleanCode) {
      deliverControl({ c: 'error', msg: 'رمز الغرفة غير صالح' });
      return;
    }

    ensurePeerJs(
      function () {
        if (mySession !== activeSession) return;
        var p;
        try {
          p = new global.Peer(NET_CONFIG.peerIdPrefix + cleanCode, createPeerOptions());
        } catch (_) {
          if (mySession === activeSession) {
            cleanup();
            deliverControl({ c: 'error', msg: 'تعذر تهيئة الاتصال الشبكي' });
          }
          return;
        }
        peer = p;

        p.on('open', function () {
          if (mySession !== activeSession) return;
          deliverControl({ c: 'connected' });
          deliverControl({ c: 'topology', star: true });
        });

        p.on('connection', function (conn) {
          if (mySession !== activeSession) {
            try { conn.close(); } catch (_) {}
            return;
          }
          conn.on('open', function () {
            if (mySession !== activeSession) {
              try { conn.close(); } catch (_) {}
              return;
            }
            hostConns.add(conn);
          });
          conn.on('data', function (data) {
            if (mySession !== activeSession) return;
            deliver(typeof data === 'string' ? data : JSON.stringify(data));
          });
          conn.on('close', function () {
            hostConns.delete(conn);
          });
          conn.on('error', function () {
            hostConns.delete(conn);
          });
        });

        p.on('error', function (err) {
          if (mySession !== activeSession) return;
          var msg = (err && err.type === 'unavailable-id')
            ? 'الرمز مستخدم، أنشئ غرفة جديدة'
            : 'تعذر إنشاء الغرفة، تحقق من الإنترنت';
          cleanup();
          deliverControl({ c: 'error', msg: msg });
        });

        p.on('disconnected', function () {
          if (mySession !== activeSession || p.destroyed) return;
          try { p.reconnect(); } catch (_) {}
        });
      },
      function () {
        if (mySession !== activeSession) return;
        cleanup();
        deliverControl({ c: 'error', msg: 'تعذر تحميل مكتبة الاتصال، تحقق من الإنترنت' });
      }
    );
  }

  function startJoin(code) {
    cleanup();
    var mySession = ++activeSession;
    role = 'client';
    clientConnected = false;

    var cleanCode = String(code || '').trim().toUpperCase();
    if (!cleanCode) {
      deliverControl({ c: 'error', msg: 'اكتب رمز الغرفة أولاً' });
      return;
    }

    joinTimer = setTimeout(function () {
      if (mySession !== activeSession || clientConnected) return;
      cleanup();
      deliverControl({ c: 'error', msg: 'لم يتم العثور على الغرفة' });
    }, NET_CONFIG.joinTimeoutMs);

    ensurePeerJs(
      function () {
        if (mySession !== activeSession) return;
        var p;
        try {
          p = new global.Peer(undefined, createPeerOptions());
        } catch (_) {
          if (mySession === activeSession) {
            cleanup();
            deliverControl({ c: 'error', msg: 'تعذر تهيئة الاتصال الشبكي' });
          }
          return;
        }
        peer = p;

        p.on('open', function () {
          if (mySession !== activeSession) return;
          var conn = p.connect(NET_CONFIG.peerIdPrefix + cleanCode, { reliable: true });
          clientConn = conn;

          conn.on('open', function () {
            if (mySession !== activeSession) {
              try { conn.close(); } catch (_) {}
              return;
            }
            clearJoinTimer();
            clientConnected = true;
            deliverControl({ c: 'connected' });
            if (pendingPreOpen.length > 0) {
              for (var i = 0; i < pendingPreOpen.length; i++) {
                try { conn.send(pendingPreOpen[i]); } catch (_) {}
              }
              pendingPreOpen.length = 0;
            }
          });

          conn.on('data', function (data) {
            if (mySession !== activeSession) return;
            deliver(typeof data === 'string' ? data : JSON.stringify(data));
          });

          conn.on('close', function () {
            if (mySession !== activeSession) return;
            var wasConnected = clientConnected;
            cleanup();
            if (wasConnected) {
              deliverControl({ c: 'disconnected' });
            } else {
              deliverControl({ c: 'error', msg: 'لم يتم العثور على الغرفة' });
            }
          });

          conn.on('error', function () {
            if (mySession !== activeSession) return;
            var wasConnected = clientConnected;
            cleanup();
            deliverControl(
              wasConnected
                ? { c: 'disconnected' }
                : { c: 'error', msg: 'لم يتم العثور على الغرفة' }
            );
          });
        });

        p.on('error', function () {
          if (mySession !== activeSession) return;
          if (!clientConnected) {
            cleanup();
            deliverControl({ c: 'error', msg: 'لم يتم العثور على الغرفة' });
          }
        });
      },
      function () {
        if (mySession !== activeSession) return;
        cleanup();
        deliverControl({ c: 'error', msg: 'تعذر تحميل مكتبة الاتصال، تحقق من الإنترنت' });
      }
    );
  }

  // Register global bridge BEFORE the game script executes.
  global.sendNetworkData = function (str) {
    if (typeof str !== 'string') return;

    // Fast path check for control messages without parsing large game envelopes.
    if (str.length < 256 && str.slice(0, 24).indexOf('"c"') !== -1) {
      var ctl = null;
      try { ctl = JSON.parse(str); } catch (_) {}
      if (ctl && typeof ctl.c === 'string') {
        if (ctl.c === 'host') {
          startHost(ctl.code);
        } else if (ctl.c === 'join') {
          startJoin(ctl.code);
        } else if (ctl.c === 'leave') {
          cleanup();
        }
        return;
      }
    }

    // Opaque game envelope transport (hot path: zero logging, zero alteration).
    if (role === 'host') {
      hostConns.forEach(function (conn) {
        if (conn.open) {
          try { conn.send(str); } catch (_) {}
        }
      });
    } else if (role === 'client') {
      if (clientConn && clientConn.open) {
        try { clientConn.send(str); } catch (_) {}
      } else if (!clientConnected && pendingPreOpen.length < 2) {
        pendingPreOpen.push(str);
      }
    }
  };
})(window);
