
const FALLBACK_IMAGE = "/news-placeholder.svg";

function formatDate(value) {
  if (!value || Number.isNaN(new Date(value).getTime())) return "Date unavailable";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function readableText(value, fallback) {
  if (!value) return fallback;
  const element = document.createElement("textarea");
  element.innerHTML = value.replace(/<[^>]*>/g, " ");
  return element.value.replace(/\s+/g, " ").trim();
}

function sourceStyle(source) {
  return source?.toLowerCase().includes("guardian") ? "guardian" : "nyt";
}

function StoryCard({ article, variant = "standard" }) {
  const title = article.title || "Untitled story";
  const description = readableText(article.description, "Open the original article to read the complete story.");
  const isLead = variant === "lead";
  const isSupporting = variant === "supporting";
  const className = [
    "story-card",
    isLead ? "featured-story" : "",
    isSupporting ? "supporting-story" : "",
  ].filter(Boolean).join(" ");

  return <article className={className}>
    <div className="image-wrap">
      <img
        src={article.imageUrl || FALLBACK_IMAGE}
        onError={(event) => { event.currentTarget.onerror = null; if (!event.currentTarget.src.endsWith(FALLBACK_IMAGE)) event.currentTarget.src = FALLBACK_IMAGE; }}
        alt=""
        loading={isLead ? "eager" : "lazy"}
      />
      {isLead && <span className="lead-label">LEAD STORY</span>}
      {isSupporting && <span className="focus-label">IN FOCUS</span>}
    </div>
    <div className="story-content">
      <div className="story-meta">
        <span className={`source-pill ${sourceStyle(article.source)}`}>{article.source || "News"}</span>
        <time dateTime={article.publishedAt || undefined}>{formatDate(article.publishedAt)}</time>
      </div>
      <h3><a href={article.url} target="_blank" rel="noreferrer noopener">{title}</a></h3>
      <p className="story-description">{description}</p>
      <a className="read-link" href={article.url} target="_blank" rel="noreferrer noopener" aria-label={`Read ${title} on ${article.source || "source"}`}>
        Read original <span aria-hidden="true">↗</span>
      </a>
    </div>
  </article>;
}

function LoadingState() {
  return <div aria-label="Loading news">
    <div className="lead-layout">
      <div className="story-card featured-story skeleton"><div className="skeleton-image" /><div className="story-content"><i /><i /><i /><i /></div></div>
      <div className="story-card supporting-story skeleton"><div className="skeleton-image" /><div className="story-content"><i /><i /><i /></div></div>
    </div>
    <div className="news-grid skeleton-grid">
      {Array.from({ length: 8 }, (_, index) => <div className="story-card skeleton" key={index}><div className="skeleton-image" /><div className="story-content"><i /><i /><i /></div></div>)}
    </div>
  </div>;
}

export default function NewsList({ articles, loading, error, onRetry }) {
  if (error) return <section className="state-card error-state"><span aria-hidden="true">!</span><p className="state-label">REQUEST FAILED</p><h3>We lost the news signal</h3><p>{error}</p><button type="button" onClick={onRetry}>Try again <span aria-hidden="true">→</span></button></section>;
  if (loading) return <LoadingState />;
  if (!articles.length) return <section className="state-card"><span aria-hidden="true">⌕</span><p className="state-label">NO RESULTS</p><h3>No matching stories</h3><p>Try a broader topic, another company name, or check your spelling.</p></section>;

  const [leadStory, supportingStory, ...otherStories] = articles;

  return <div className="news-list">
    <div className={`lead-layout${supportingStory ? "" : " single"}`}>
      <StoryCard article={leadStory} variant="lead" />
      {supportingStory && <StoryCard article={supportingStory} variant="supporting" />}
    </div>
    {otherStories.length > 0 && <div className="news-grid">
      {otherStories.map((article, index) => <StoryCard article={article} key={article.url || `${article.title}-${index}`} />)}
    </div>}
  </div>;
}
