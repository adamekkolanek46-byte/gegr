/*
  Nexus System Template
  https://templatemo.com/tm-629-nexus-system
*/

(function () {
  "use strict";

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  var cards = Array.prototype.slice.call(document.querySelectorAll(".zcard"));
  var hudFill = document.getElementById("hudFill");
  var hudPct = document.getElementById("hudPct");
  var navUp = document.getElementById("navUp");
  var navDown = document.getElementById("navDown");

  var vh = window.innerHeight;
  var ticking = false;
  var spacer = document.querySelector(".scroll-spacer");

  /* spacer = liczba kart x aktualna wysokość ekranu (na telefonach pasek adresu zmienia vh) */
  function sizeSpacer() {
    if (spacer) { spacer.style.height = (cards.length * vh) + "px"; }
  }
  sizeSpacer();

  function clamp01(v) { return Math.max(0, Math.min(1, v)); }

  /* the z-depth escalator engine */
  function renderEngine() {
    var y = window.scrollY || window.pageYOffset || 0;
    var i, entry, recede, scale, ty;

    for (i = 0; i < cards.length; i++) {
      entry = i === 0 ? 1 : clamp01((y - (i - 1) * vh) / vh);
      recede = i === cards.length - 1 ? 0 : clamp01((y - i * vh) / vh);
      scale = 1 - 0.1 * recede;
      ty = (1 - entry) * 100;
      cards[i].style.transform = "translateY(" + ty + "%) scale(" + scale + ")";
      cards[i].style.opacity = String(1 - 0.6 * recede);
    }
  }

  /* hud scroll progress, works in both engine and static modes */
  function renderProgress() {
    var doc = document.documentElement;
    var max = Math.max(1, doc.scrollHeight - window.innerHeight);
    var p = clamp01((window.scrollY || window.pageYOffset || 0) / max);
    hudFill.style.width = (p * 100) + "%";
    hudPct.textContent = String(Math.round(p * 100)).padStart(3, "0") + "%";
    navUp.disabled = p <= 0.001;
    navDown.disabled = p >= 0.999;
  }

  function onFrame() {
    if (!reduceMotion) { renderEngine(); }
    renderProgress();
    ticking = false;
  }

  function requestRender() {
    if (!ticking) {
      ticking = true;
      window.requestAnimationFrame(onFrame);
    }
  }

  window.addEventListener("scroll", requestRender, { passive: true });

  window.addEventListener("resize", function () {
    vh = window.innerHeight;
    sizeSpacer();
    requestRender();
  });

  /* fallback render for iframe previews where scroll may never fire */
  setTimeout(requestRender, 3000);
  requestRender();

  /* up and down arrow nav: snaps to exact card boundaries */
  function gotoCard(index) {
    var i = Math.max(0, Math.min(cards.length - 1, index));
    var top;

    if (reduceMotion) {
      top = cards[i].getBoundingClientRect().top + (window.scrollY || window.pageYOffset || 0);
      window.scrollTo(0, top);
    } else {
      window.scrollTo({ top: i * vh, behavior: "smooth" });
    }
  }

  navUp.addEventListener("click", function () {
    var y = window.scrollY || window.pageYOffset || 0;
    gotoCard(Math.ceil(y / vh - 0.001) - 1);
  });

  navDown.addEventListener("click", function () {
    var y = window.scrollY || window.pageYOffset || 0;
    gotoCard(Math.floor(y / vh + 0.001) + 1);
  });

  /* kinetic accordion: hover on fine pointers, tap or click everywhere */
  var accordion = document.getElementById("accordion");
  var slices = Array.prototype.slice.call(accordion.querySelectorAll(".slice"));
  var hoverCapable = window.matchMedia("(hover: hover) and (pointer: fine)").matches;

  function setActiveSlice(target) {
    slices.forEach(function (s) {
      var on = s === target;
      s.classList.toggle("active", on);
      s.setAttribute("aria-expanded", on ? "true" : "false");
    });
  }

  slices.forEach(function (slice) {
    if (hoverCapable) {
      slice.addEventListener("mouseenter", function () { setActiveSlice(slice); });
      slice.addEventListener("focus", function () { setActiveSlice(slice); });
    }
    slice.addEventListener("click", function () {
      if (slice.classList.contains("active") && !hoverCapable) {
        setActiveSlice(null);
      } else {
        setActiveSlice(slice);
      }
    });
  });

  if (hoverCapable) {
    accordion.addEventListener("mouseleave", function () { setActiveSlice(null); });
  }

  /* phones and touch screens: first column is open right away */
  if (!hoverCapable) { setActiveSlice(slices[0]); }

  /* matrix tabs with data-goto switching */
  var tabs = Array.prototype.slice.call(document.querySelectorAll(".matrix-tab"));
  var panels = Array.prototype.slice.call(document.querySelectorAll(".matrix-img"));
  var caption = document.getElementById("matrixCaption");

  var captions = {
    core: "FOTO 01 / EKIPA",
    fluidics: "FOTO 02 / WSPOMNIENIA",
    sync: "FOTO 03 / DOŁĄCZ"
  };

  tabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      var key = tab.getAttribute("data-goto");

      tabs.forEach(function (t) {
        var on = t === tab;
        t.classList.toggle("active", on);
        t.setAttribute("aria-selected", on ? "true" : "false");
      });

      panels.forEach(function (p) {
        p.classList.toggle("active", p.getAttribute("data-panel") === key);
      });

      caption.textContent = captions[key];
    });
  });

  /* galeria + lightbox (klik, strzałki, swipe, Esc) */
  var galItems = Array.prototype.slice.call(document.querySelectorAll(".gal-item"));
  var lb = document.getElementById("lightbox");
  var lbImg = document.getElementById("lbImg");
  var lbCount = document.getElementById("lbCount");
  var lbIndex = 0;

  function lbShow(i) {
    lbIndex = (i + galItems.length) % galItems.length;
    var im = galItems[lbIndex].querySelector("img");
    lbImg.src = im.getAttribute("src");
    lbImg.alt = im.getAttribute("alt") || "";
    lbCount.textContent = String(lbIndex + 1).padStart(2, "0") + " / " + String(galItems.length).padStart(2, "0");
  }
  function lbOpen(i) { lbShow(i); lb.classList.add("open"); lb.setAttribute("aria-hidden", "false"); }
  function lbClose() { lb.classList.remove("open"); lb.setAttribute("aria-hidden", "true"); }

  galItems.forEach(function (it, i) { it.addEventListener("click", function () { lbOpen(i); }); });
  document.getElementById("lbClose").addEventListener("click", lbClose);
  document.getElementById("lbPrev").addEventListener("click", function () { lbShow(lbIndex - 1); });
  document.getElementById("lbNext").addEventListener("click", function () { lbShow(lbIndex + 1); });
  lb.addEventListener("click", function (e) { if (e.target === lb) { lbClose(); } });

  document.addEventListener("keydown", function (e) {
    if (!lb.classList.contains("open")) { return; }
    if (e.key === "Escape") { lbClose(); }
    else if (e.key === "ArrowLeft") { lbShow(lbIndex - 1); }
    else if (e.key === "ArrowRight") { lbShow(lbIndex + 1); }
  });

  var tx = 0, ty = 0;
  lb.addEventListener("touchstart", function (e) { tx = e.touches[0].clientX; ty = e.touches[0].clientY; }, { passive: true });
  lb.addEventListener("touchend", function (e) {
    var dx = e.changedTouches[0].clientX - tx;
    var dy = e.changedTouches[0].clientY - ty;
    if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy)) { lbShow(lbIndex + (dx < 0 ? 1 : -1)); }
    else if (dy > 90 && Math.abs(dy) > Math.abs(dx)) { lbClose(); }
  }, { passive: true });
})();
