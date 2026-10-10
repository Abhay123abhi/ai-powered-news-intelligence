import { beforeEach, describe, expect, it, vi } from "vitest";
const get = vi.hoisted(() => vi.fn());
vi.mock("axios", () => ({ default: { create: () => ({ get }) } }));
import newsApi from "./newsApi";
describe("newsApi.search", () => {
  beforeEach(() => get.mockReset());
  it("normalizes search terms and forwards cancellation", async () => {
    const controller = new AbortController();
    get.mockResolvedValue({ data: { articles: [], page: 2 } });
    const result = await newsApi.search(" climate ", 2, 8, controller.signal);
    expect(get).toHaveBeenCalledWith("/news", {
      params: { keyword: "climate", page: 2, pageSize: 8 },
      signal: controller.signal
    });
    expect(result).toEqual({ articles: [], page: 2 });
  });
});
