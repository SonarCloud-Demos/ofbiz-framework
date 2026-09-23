(function () {
  "use strict";

  const routes = window.OFBIZ_ROUTE_MANIFEST;
  const match = Object.keys(routes)
    .sort((left, right) => right.length - left.length)
    .find((prefix) => window.location.pathname.startsWith(prefix));
  const route = match ? routes[match] : null;
  const marker = document.querySelector("[data-generation]");

  if (!route || !["modern", "hybrid"].includes(route.generation)) {
    marker.textContent = "Route configuration error";
    marker.dataset.generation = "error";
    document.querySelector("main").setAttribute("hidden", "");
    return;
  }

  const label = route.generation === "modern" ? "Modern" : "Hybrid";
  marker.textContent = label;
  marker.dataset.generation = route.generation;
  marker.setAttribute("aria-label", `Application generation: ${label.toLowerCase()}`);
  document.documentElement.dataset.routeOwner = route.owner;

  const error = document.querySelector("#shell-error");
  const signOut = document.querySelector("#sign-out");
  let csrf;

  function showError(message) {
    error.textContent = message;
    error.hidden = false;
  }

  fetch("/bff/session", { credentials: "same-origin", headers: { Accept: "application/json" } })
    .then((response) => {
      if (!response.ok) throw new Error(`Session request failed: ${response.status}`);
      return response.json();
    })
    .then((session) => {
      csrf = session.csrf;
      document.querySelector("#session-user").textContent = session.displayName || "Signed in";
      signOut.hidden = false;
    })
    .catch(() => showError("Your session could not be loaded. Refresh the page or sign in again."));

  signOut.addEventListener("click", () => {
    if (!csrf) return;
    fetch("/bff/logout", {
      method: "POST",
      credentials: "same-origin",
      headers: { [csrf.headerName]: csrf.token }
    }).then((response) => {
      if (!response.ok) throw new Error(`Logout failed: ${response.status}`);
      window.location.assign("/oauth2/authorization/entra");
    }).catch(() => showError("Sign out failed. Please retry."));
  });
})();
