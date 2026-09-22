import axios from "axios";

const client = axios.create({
  baseURL: "/api",
  timeout: 90000,
  headers: { Accept: "application/json" }
});

const newsApi = {
  async search(keyword, page, pageSize, feedId, signal) {
    const { data } = await client.get("/news", {
      params: { keyword: keyword.trim(), page, pageSize, feedId }, signal
    });
    return data;
  }
};

export default newsApi;
