(function () {
  if (window.VoiceCleanAndroid || !("serviceWorker" in navigator) || !window.isSecureContext) return;
  navigator.serviceWorker.register("sw.js").then(function (reg) {
    if (window.crossOriginIsolated) return;
    var reload = function () {
      try {
        if (sessionStorage.getItem("vc-coi-reload")) return;
        sessionStorage.setItem("vc-coi-reload", "1");
      } catch (e) {
        return;
      }
      window.location.reload();
    };
    if (reg.active && navigator.serviceWorker.controller) {
      reload();
    } else {
      navigator.serviceWorker.addEventListener("controllerchange", reload);
    }
  }).catch(function () {});
})();
