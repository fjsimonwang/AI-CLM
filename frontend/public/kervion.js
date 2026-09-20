(function () {
  var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* year */
  var yr = document.getElementById('yr');
  if (yr) yr.textContent = new Date().getFullYear();

  /* nav background on scroll */
  var nav = document.getElementById('nav');
  var onScroll = function () { nav.classList.toggle('scrolled', window.scrollY > 40); };
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  /* reveal on scroll */
  var items = document.querySelectorAll('[data-reveal]');
  if (reduce || !('IntersectionObserver' in window)) {
    items.forEach(function (el) { el.classList.add('in'); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.06 });
    items.forEach(function (el) { io.observe(el); });
  }

  /* lifecycle chips: slow sequential highlight while in view */
  var stages = document.getElementById('stages');
  if (stages && !reduce) {
    var chips = stages.querySelectorAll('.stage'), i = 0, timer = null;
    var tick = function () {
      chips.forEach(function (c) { c.classList.remove('lit'); });
      chips[i % chips.length].classList.add('lit');
      i++;
    };
    var vis = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting && !timer) { tick(); timer = setInterval(tick, 1400); }
        else if (!e.isIntersecting && timer) { clearInterval(timer); timer = null; }
      });
    }, { threshold: 0.25 });
    vis.observe(stages);
  }

  /* ---- the glass stack: scroll-driven layer separation --------------- */
  (function () {
    var wrap   = document.getElementById('anatomy');
    var stack  = document.getElementById('glassStack');
    var list   = document.getElementById('layerList');
    var hint   = document.getElementById('stackHint');
    if (!wrap || !stack || !list) return;

    var plates = Array.prototype.slice.call(stack.children);
    var items  = Array.prototype.slice.call(list.children);
    var N      = plates.length;
    var OPEN   = 0.26;            /* share of the scroll spent prising the stack apart */
    var GAP_MIN = 6, GAP_MAX = 58;
    var current = -1;

    var setActive = function (i) {
      if (i === current) return;
      current = i;
      for (var k = 0; k < N; k++) {
        plates[k].classList.toggle('on', k === i);
        items[k].classList.toggle('on', k === i);
      }
    };

    /* small screens / reduced motion: no pinning, one calm reveal instead */
    var small = window.matchMedia('(max-width: 980px)');
    var isStatic = function () { return reduce || small.matches; };

    var goStatic = function () {
      wrap.classList.add('static');
      stack.style.setProperty('--gap', '30px');
      stack.style.setProperty('--tilt', '52deg');
      stack.style.setProperty('--lo', '1');
      plates.forEach(function (pl) { pl.classList.remove('on'); });
      items.forEach(function (it) { it.classList.add('on'); });
      current = -1;
    };

    var raf = null;
    var frame = function () {
      raf = null;
      var r = wrap.getBoundingClientRect();
      var total = r.height - window.innerHeight;
      var p = total > 0 ? -r.top / total : 0;
      p = p < 0 ? 0 : p > 1 ? 1 : p;

      /* phase 1 — the panes lift apart */
      var open = Math.min(1, p / OPEN);
      var eased = 1 - Math.pow(1 - open, 3);
      stack.style.setProperty('--gap', (GAP_MIN + (GAP_MAX - GAP_MIN) * eased).toFixed(2) + 'px');
      stack.style.setProperty('--tilt', (58 - 7 * eased).toFixed(2) + 'deg');
      stack.style.setProperty('--lo', Math.min(1, eased * 1.6).toFixed(3));

      /* phase 2 — walk down the layers */
      var q = (p - OPEN) / (1 - OPEN);
      setActive(q <= 0 ? -1 : Math.min(N - 1, Math.floor(q * N)));

      if (hint) hint.style.setProperty('--prog', (p * 100).toFixed(1) + '%');
    };
    var onScroll = function () { if (raf === null) raf = requestAnimationFrame(frame); };

    var live = false;
    var sync = function () {
      if (isStatic()) {
        if (live) { window.removeEventListener('scroll', onScroll); live = false; }
        goStatic();
      } else {
        wrap.classList.remove('static');
        items.forEach(function (it) { it.classList.remove('on'); });
        if (!live) { window.addEventListener('scroll', onScroll, { passive: true }); live = true; }
        frame();
      }
    };
    sync();
    window.addEventListener('resize', sync, { passive: true });

    /* clicking a layer scrolls to the point where it is the active one */
    items.forEach(function (it, i) {
      it.querySelector('.lrow').addEventListener('click', function () {
        if (isStatic()) return;
        var top = wrap.offsetTop;
        var total = wrap.offsetHeight - window.innerHeight;
        var p = OPEN + (1 - OPEN) * ((i + 0.5) / N);
        window.scrollTo({ top: top + total * p, behavior: reduce ? 'auto' : 'smooth' });
      });
    });
  })();

  /* team tabs */
  var tabs = Array.prototype.slice.call(document.querySelectorAll('.tab'));
  var select = function (tab) {
    tabs.forEach(function (t) {
      var on = t === tab;
      t.setAttribute('aria-selected', on ? 'true' : 'false');
      document.getElementById(t.getAttribute('aria-controls')).hidden = !on;
    });
  };
  tabs.forEach(function (t, idx) {
    t.addEventListener('click', function () { select(t); });
    t.addEventListener('keydown', function (e) {
      var d = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
      if (!d) return;
      e.preventDefault();
      var next = tabs[(idx + d + tabs.length) % tabs.length];
      next.focus(); select(next);
    });
  });

  /* smooth in-page anchors */
  document.querySelectorAll('a[href^="#"]').forEach(function (a) {
    a.addEventListener('click', function (e) {
      var el = document.querySelector(a.getAttribute('href'));
      if (!el) return;
      e.preventDefault();
      el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
    });
  });
  /* AI assistant — floating bubble that opens a small chat window, every page */
  (function () {
    var URL = 'https://www.wreathflow.com/external/R2Op3iP20lIZ0-m_XS_92O66LLFXgp8X';
    var CHAT = '<svg class="ic-chat" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 11.5C4 7.36 7.58 4 12 4s8 3.36 8 7.5S16.42 19 12 19c-1.05 0-2.05-.19-2.97-.53L5 20l1.2-3.37A7.1 7.1 0 0 1 4 11.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M8.6 11.6h.01M12 11.6h.01M15.4 11.6h.01" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>';
    var CLOSE = '<svg class="ic-close" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';

    var box = document.createElement('div');
    box.className = 'assist';
    box.innerHTML =
      '<div class="assist-note" role="button" tabindex="0" aria-label="Chat with our AI assistant">' +
        '<button class="assist-x" type="button" aria-label="Dismiss">&times;</button>' +
        '<b>Questions about Kervion CLM?</b><span>Ask our AI assistant &mdash; it answers in seconds.</span>' +
      '</div>' +
      '<button class="assist-btn" type="button" aria-label="Chat with our AI assistant" aria-expanded="false" aria-controls="assistPanel">' +
        CHAT + CLOSE + '<span class="live" aria-hidden="true"></span>' +
      '</button>';

    var panel = document.createElement('section');
    panel.className = 'assist-panel';
    panel.id = 'assistPanel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', 'Kervion AI assistant');
    panel.setAttribute('aria-hidden', 'true');
    panel.innerHTML =
      '<header class="assist-head">' +
        '<span class="av">' + CHAT + '</span>' +
        '<span class="tt"><b>Kervion AI assistant</b><span>Online</span></span>' +
        '<a class="assist-ic" href="' + URL + '" target="_blank" rel="noopener noreferrer" title="Open in a new tab" aria-label="Open in a new tab">' +
          '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M14 4h6v6M20 4l-8.5 8.5M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg></a>' +
        '<button class="assist-ic" type="button" data-close aria-label="Close chat">' + CLOSE.replace('class="ic-close" ', '') + '</button>' +
      '</header>' +
      '<div class="assist-body"><div class="assist-load"><i></i>Connecting&hellip;</div></div>';

    document.body.appendChild(panel);
    document.body.appendChild(box);

    var btn = box.querySelector('.assist-btn');
    var note = box.querySelector('.assist-note');
    var body = panel.querySelector('.assist-body');
    var KEY = 'kervion-assist-dismissed';
    var loaded = false, isOpen = false;

    var hideNote = function (remember) {
      note.classList.remove('show');
      setTimeout(function () { note.hidden = true; }, 450);
      if (remember) { try { sessionStorage.setItem(KEY, '1'); } catch (e) {} }
    };

    var setOpen = function (on) {
      isOpen = on;
      if (on && !loaded) {           /* load the chat only when first asked for */
        var f = document.createElement('iframe');
        f.src = URL;
        f.title = 'Kervion AI assistant';
        f.allow = 'clipboard-write; microphone';
        f.addEventListener('load', function () {
          var l = body.querySelector('.assist-load'); if (l) l.remove();
        });
        body.appendChild(f);
        loaded = true;
      }
      panel.classList.toggle('open', on);
      box.classList.toggle('is-open', on);
      panel.setAttribute('aria-hidden', on ? 'false' : 'true');
      btn.setAttribute('aria-expanded', on ? 'true' : 'false');
      btn.setAttribute('aria-label', on ? 'Close chat' : 'Chat with our AI assistant');
      if (on) hideNote(true);
    };

    btn.addEventListener('click', function () { setOpen(!isOpen); });
    panel.querySelector('[data-close]').addEventListener('click', function () { setOpen(false); btn.focus(); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && isOpen) { setOpen(false); btn.focus(); }
    });

    note.addEventListener('click', function (e) {
      if (e.target.closest('.assist-x')) { hideNote(true); return; }
      setOpen(true);
    });
    note.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setOpen(true); }
    });

    var dismissed = false;
    try { dismissed = sessionStorage.getItem(KEY) === '1'; } catch (e) {}
    if (dismissed) note.hidden = true;
    else setTimeout(function () { if (!isOpen) note.classList.add('show'); }, 2600);
  })();
})();
