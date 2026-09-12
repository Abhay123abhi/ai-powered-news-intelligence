import { useCallback, useEffect, useMemo, useState } from "react";
import newsApi from "./features/news/api/newsApi";
import SearchForm from "./features/news/components/SearchForm";
import NewsList from "./features/news/components/NewsList";
import AiWorkspace from "./features/ai/components/AiWorkspace";
import "./App.css";
import "./ai-first.css";

const DEFAULT_QUERY = "latest";
const DEFAULT_PAGE_SIZE = 12;
const QUICK_TOPICS = ["latest", "technology", "business", "climate", "science", "world"];

function getSavedTheme() {
  const savedTheme = window.localStorage.getItem("news-theme");
  if (savedTheme) return savedTheme;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function labelForTopic(topic) {
  return topic === "latest" ? "Top stories" : topic.charAt(0).toUpperCase() + topic.slice(1);
}

export default function App() {
  const [theme, setTheme] = useState(getSavedTheme);
  const [search, setSearch] = useState({ keyword: DEFAULT_QUERY, pageSize: DEFAULT_PAGE_SIZE });
  const [data, setData] = useState({ articles: [], page: 1, totalPages: 1 });
  const [loading, setLoading] = useState(true);
  const [slowRequest, setSlowRequest] = useState(false);
  const [error, setError] = useState("");

  const loadNews = useCallback(async (keyword, page, pageSize) => {
    setLoading(true);
    setSlowRequest(false);
    setError("");
    try {
      const response = await newsApi.search(keyword, page, pageSize);
      setData(response);
      setSearch({ keyword, pageSize });
    } catch (requestError) {
      setError(
        requestError.response?.data?.detail ||
        requestError.response?.data?.message ||
        "We couldn't load the news right now. Please try again."
      );
    } finally {
      setLoading(false);
      setSlowRequest(false);
    }
  }, []);

  useEffect(() => { loadNews(DEFAULT_QUERY, 1, DEFAULT_PAGE_SIZE); }, [loadNews]);
  useEffect(() => {
    if (!loading) return undefined;
    const timer = window.setTimeout(() => setSlowRequest(true), 10000);
    return () => window.clearTimeout(timer);
  }, [loading]);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem("news-theme", theme);
  }, [theme]);

  const resultSummary = useMemo(() => {
    if (loading) return "Building your briefing…";
    if (!data.articles?.length) return "No stories found";
    return labelForTopic(data.searchKeyword || search.keyword);
  }, [data, loading, search.keyword]);

  const sourceCount = useMemo(
    () => new Set((data.articles || []).map((article) => article.source).filter(Boolean)).size,
    [data.articles]
  );

  const handleSearch = (keyword, pageSize) => loadNews(keyword, 1, pageSize);
  const handlePageChange = (page) => loadNews(search.keyword, page, search.pageSize);
  const handleTopicChange = (topic) => loadNews(topic, 1, search.pageSize);

  return <div className="app-shell">
    <header className="site-header">
      <nav className="topbar" aria-label="Primary navigation">
        <a className="brand" href="/" aria-label="Newsroom home">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <span className="brand-copy"><strong>Newsroom</strong><small>INTELLIGENCE</small></span>
        </a>
        <div className="nav-links" aria-label="Page sections">
          <a href="#ai-workspace">AI workspace <span>Live</span></a>
          <a href="#stories">Discover</a>
        </div>
        <button className="theme-toggle" type="button" onClick={() => setTheme((value) => value === "dark" ? "light" : "dark")} aria-label={`Switch to ${theme === "dark" ? "light" : "dark"} theme`}>
          <span aria-hidden="true">{theme === "dark" ? "☀" : "☾"}</span>
          <span className="theme-label">{theme === "dark" ? "Light" : "Dark"}</span>
        </button>
      </nav>
    </header>

    <main>
      <section className="hero">
        <div className="hero-copy-block">
          <div className="live-label"><span aria-hidden="true" /> AI-POWERED NEWS DISCOVERY</div>
          <h1>Search the news.<br /><em>Work with it.</em></h1>
          <p>Search trusted reporting from The Guardian and The New York Times, then use the AI workspace to brief, question, and compare the stories already in your feed.</p>

          <SearchForm initialKeyword={search.keyword} initialPageSize={search.pageSize} onSearch={handleSearch} loading={loading} />

          <div className="quick-topics" aria-label="Quick topics">
            <span>Explore</span>
            {QUICK_TOPICS.map((topic) => <button className={search.keyword.toLowerCase() === topic ? "selected" : ""} type="button" key={topic} onClick={() => handleTopicChange(topic)} disabled={loading}>{labelForTopic(topic)}</button>)}
          </div>

          <div className="source-strip" aria-label="Connected news sources">
            <span className="source-strip-label">Sources</span>
            <span className="source-chip"><i className="guardian-dot" /> The Guardian</span>
            <span className="source-chip"><i className="nyt-dot" /> The New York Times</span>
            <span className="source-grounding">AI uses the stories shown in your feed</span>
          </div>
        </div>

        <div className="hero-ai">
          <div className="hero-ai-label"><span>✦</span> AI workspace</div>
          <AiWorkspace articles={data.articles || []} />
        </div>
      </section>

      <section className="content" id="stories" aria-live="polite">
        <div className="results-header">
          <div><p className="section-label">YOUR NEWS FEED</p><h2>{resultSummary}</h2></div>
          {!loading && !error && <div className="result-metrics"><span><strong>{data.articles?.length || 0}</strong> shown</span><span><strong>{sourceCount}</strong> sources</span>{data.timeTakenMs != null && <span><strong>{data.timeTakenMs < 1000 ? `${data.timeTakenMs}ms` : `${(data.timeTakenMs / 1000).toFixed(1)}s`}</strong> response</span>}</div>}
        </div>

        {slowRequest && <div className="startup-banner" role="status"><span aria-hidden="true">◷</span><div><strong>Waking the news service</strong><p>The free server is starting. Your first request can take about a minute.</p></div></div>}

        <div className="feed-column">
          <NewsList articles={data.articles || []} loading={loading} error={error} onRetry={() => loadNews(search.keyword, data.page || 1, search.pageSize)} />
          {!loading && !error && data.articles?.length > 0 && <nav className="pagination" aria-label="News result pages">
            <button type="button" onClick={() => handlePageChange(data.prevPage)} disabled={!data.prevPage}><span aria-hidden="true">←</span> Previous</button>
            <span>Page <strong>{data.page || 1}</strong>{data.totalPages > 1 && ` of ${data.totalPages}`}</span>
            <button type="button" onClick={() => handlePageChange(data.nextPage)} disabled={!data.nextPage}>Next <span aria-hidden="true">→</span></button>
          </nav>}
        </div>
      </section>
    </main>

    <footer><div><span className="footer-brand">Newsroom Intelligence</span><p>Reporting stays with its original publisher. Open every source for complete context.</p></div><span>Guardian + NYT</span></footer>
  </div>;
}
