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

  if (window.location.pathname.startsWith("/catalog/products")) {
    document.querySelector("#home-view").hidden = true;
    const view = document.querySelector("#catalog-view");
    const form = document.querySelector("#catalog-search");
    const results = document.querySelector("#catalog-results");
    const status = document.querySelector("#catalog-status");
    const detail = document.querySelector("#catalog-detail");
    view.hidden = false;

    function textElement(tag, value) {
      const element = document.createElement(tag);
      element.textContent = value || "—";
      return element;
    }

    function showDetail(id) {
      status.textContent = "Loading product…";
      fetch(`/bff/catalog/products/${encodeURIComponent(id)}`, { headers: { Accept: "application/json" } })
        .then((response) => { if (!response.ok) throw new Error(); return response.json(); })
        .then((product) => {
          detail.replaceChildren(textElement("h2", product.productName || product.productId),
            textElement("p", `Product ID: ${product.productId}`),
            textElement("p", `Type: ${product.productTypeId || "—"}`),
            textElement("p", `Status: ${product.statusId || "—"}`),
            textElement("p", product.description));
          detail.hidden = false;
          status.textContent = "";
        }).catch(() => { status.textContent = "Product details could not be loaded."; });
    }

    function search(event) {
      if (event) event.preventDefault();
      detail.hidden = true;
      results.replaceChildren();
      status.textContent = "Searching…";
      const query = new URLSearchParams(new FormData(form));
      fetch(`/bff/catalog/products?${query}`, { headers: { Accept: "application/json" } })
        .then((response) => { if (!response.ok) throw new Error(); return response.json(); })
        .then((page) => {
          page.items.forEach((product) => {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = `${product.productId} — ${product.productName || product.internalName || "Unnamed"}`;
            button.addEventListener("click", () => showDetail(product.productId));
            const item = document.createElement("li"); item.append(button); results.append(item);
          });
          status.textContent = page.items.length ? `${page.items.length} products` : "No products found.";
        }).catch(() => { status.textContent = "Product search could not be loaded."; });
    }
    form.addEventListener("submit", search);
    search();
    const directProduct = window.location.pathname.match(/^\/catalog\/products\/([^/]+)$/);
    if (directProduct) showDetail(decodeURIComponent(directProduct[1]));
  }
})();
