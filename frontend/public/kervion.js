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
})();
