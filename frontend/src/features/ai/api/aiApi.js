import axios from "axios";

const client = axios.create({
  baseURL: "/api/ai",
  timeout: 90000,
  headers: { Accept: "application/json", "Content-Type": "application/json" }
});

const aiApi = {
  async status() {
    const { data } = await client.get("/status");
    return data;
  },
  async brief(articles, signal) {
    const { data } = await client.post("/brief", { articles }, { signal });
    return data;
  },
  async compare(articles, signal) {
    const { data } = await client.post("/compare", { articles }, { signal });
    return data;
  },
  async ask(question, articles, signal) {
    const { data } = await client.post("/ask", { question, articles }, { signal });
    return data;
  }
};

export default aiApi;
