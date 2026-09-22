import { useCallback, useEffect, useMemo, useState } from "react";
import newsApi from "./features/news/api/newsApi";
import SearchForm from "./features/news/components/SearchForm";
import NewsList from "./features/news/components/NewsList";
import AiWorkspace from "./features/ai/components/AiWorkspace";
import "./App.css";
import "./ai-first.css";
import "./discover-refresh.css";
import "./premium-ai.css";
import "./premium-ai-polish.css";
import "./premium-search-mobile.css";

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
  const [mobileView, setMobileView] = useState("discover");
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

  const mobileViewNote = useMemo(
    () => mobileView === "discover"
      ? "Search, filter, and browse the latest stories in one focused feed."
      : "Generate a brief, ask grounded questions, and compare coverage from the current feed.",
    [mobileView]
  );

  const handleSearch = (keyword, pageSize) => {
    setMobileView("discover");
    loadNews(keyword, 1, pageSize);
  };
  const handlePageChange = (page) => {
    setMobileView("discover");
    loadNews(search.keyword, page, search.pageSize);
  };
  const handleTopicChange = (topic) => {
    setMobileView("discover");
    loadNews(topic, 1, search.pageSize);
  };

  return <div className={`app-shell mobile-view-${mobileView}`}>
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
      <section className="mobile-view-switcher" aria-label="Mobile workspace switcher">
        <div className="mobile-view-tabs" role="tablist" aria-label="Choose a mobile view">
          <button type="button" role="tab" aria-selected={mobileView === "discover"} className={`mobile-view-tab ${mobileView === "discover" ? "active" : ""}`} onClick={() => setMobileView("discover")}>
            <span>Discover</span>
            <small>Search + news</small>
          </button>
          <button type="button" role="tab" aria-selected={mobileView === "ai"} className={`mobile-view-tab ${mobileView === "ai" ? "active" : ""}`} onClick={() => setMobileView("ai")}>
            <span>AI workspace</span>
            <small>Brief + Q&amp;A</small>
          </button>
        </div>
        <p className="mobile-view-note">{mobileViewNote}</p>
      </section>

      <section className="hero premium-hero">
        <div className="hero-ambient" aria-hidden="true">
          <span className="ambient-orb orb-one" />
          <span className="ambient-orb orb-two" />
          <span className="ambient-grid" />
          <span className="signal-line signal-one" />
          <span className="signal-line signal-two" />
        </div>

        <div className="hero-copy-block premium-copy">
          <div className="live-label"><span aria-hidden="true" /> AI-POWERED NEWS INTELLIGENCE</div>
          <h1>Search the news.<br /><em>Think beyond it.</em></h1>
          <p>Trusted reporting becomes an AI research workspace. Search The Guardian and The New York Times, then generate a briefing, ask grounded questions, and compare how publishers frame the same story.</p>

          <div className="hero-value-row" aria-label="AI workspace capabilities">
            <span><i /> Source grounded</span>
            <span><i /> Multi-publisher</span>
            <span><i /> Gemini powered</span>
          </div>

          <SearchForm initialKeyword={search.keyword} initialPageSize={search.pageSize} onSearch={handleSearch} loading={loading} />

          <div className="quick-topics" aria-label="Quick topics">
            <span>Explore</span>
            {QUICK_TOPICS.map((topic) => <button className={search.keyword.toLowerCase() === topic ? "selected" : ""} type="button" key={topic} onClick={() => handleTopicChange(topic)} disabled={loading}>{labelForTopic(topic)}</button>)}
          </div>

          <div className="source-strip" aria-label="Connected news sources">
            <span className="source-strip-label">Sources</span>
            <span className="source-chip guardian-source"><i className="guardian-dot" /> The Guardian</span>
            <span className="source-chip nyt-source"><i className="nyt-dot" /> The New York Times</span>
          </div>
        </div>

        <div className="hero-ai premium-ai-stage">
          <div className="ai-stage-halo" aria-hidden="true" />
          <div className="ai-stage-label" aria-hidden="true"><span /> LIVE INTELLIGENCE LAYER</div>
          <AiWorkspace articles={data.articles || []} />
        </div>
      </section>

      <section className="content" id="stories" aria-live="polite">
        <div className="results-header">
          <div><p className="section-label">DISCOVER</p><h2>{resultSummary}</h2></div>
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
  </div>;
}
