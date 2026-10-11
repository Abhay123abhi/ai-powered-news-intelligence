import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

vi.mock("./features/news/api/newsApi", () => ({
  default: {
    search: vi.fn().mockResolvedValue({
      articles: [],
      page: 1,
      totalPages: 1,
      searchKeyword: "latest"
    })
  }
}));
vi.mock("./features/ai/components/AiWorkspace", () => ({
  default: () => <aside id="ai-workspace">AI workspace preview</aside>
}));

describe("mobile navigation", () => {
  let container;
  let root;

  beforeEach(async () => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true;
    localStorage.setItem("news-theme", "dark");
    vi.stubGlobal("scrollTo", vi.fn());
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => { root.render(<App />); });
  });

  afterEach(async () => {
    await act(async () => { root.unmount(); });
    container.remove();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it("switches between the news feed and AI without changing the selected theme", async () => {
    const buttons = [...container.querySelectorAll(".mobile-bottom-nav button")];
    expect(buttons).toHaveLength(2);
    expect(buttons[0].getAttribute("aria-pressed")).toBe("true");

    await act(async () => { buttons[1].click(); });
    expect(container.querySelector(".app-shell").className).toContain("mobile-view-ai");
    expect(buttons[1].getAttribute("aria-pressed")).toBe("true");
    expect(document.documentElement.dataset.theme).toBe("dark");

    await act(async () => { buttons[0].click(); });
    expect(container.querySelector(".app-shell").className).toContain("mobile-view-discover");
    expect(buttons[0].getAttribute("aria-pressed")).toBe("true");
  });

  it("keeps the AI context informational and uses the bottom navigation for Discover", async () => {
    const buttons = [...container.querySelectorAll(".mobile-bottom-nav button")];
    await act(async () => { buttons[1].click(); });

    const context = container.querySelector(".mobile-ai-context");
    expect(context.textContent).toContain("0 stories in context");
    expect(context.textContent).toContain("AI insights use news articles");
    expect(context.querySelector("button")).toBeNull();
    expect(container.querySelector(".mobile-context-action")).toBeNull();

    await act(async () => { buttons[0].click(); });
    expect(container.querySelector(".app-shell").className).toContain("mobile-view-discover");
  });

  it("keeps search in Discover and not in the bottom navigation", () => {
    const buttons = [...container.querySelectorAll(".mobile-bottom-nav button")];
    expect(buttons.map((button) => button.textContent.trim())).toEqual(["Discover", "AI workspace"]);
    expect(container.querySelector("#news-search")).not.toBeNull();
    expect(container.querySelector('.search-form button[type="submit"]')).not.toBeNull();
  });
});
