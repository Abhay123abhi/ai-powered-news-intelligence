import { useEffect, useMemo, useRef, useState } from "react";
import aiApi from "../api/aiApi";
import aiErrorMessage from "../api/aiErrorMessage";
import "./AiWorkspace.css";

function AiStatus({ enabled }) {
  return <small className={`ai-status ${enabled ? "online" : "offline"}`}>
    <span aria-hidden="true">●</span>{enabled ? "ONLINE" : "OFFLINE"}
  </small>;
}

function DisabledAiWorkspace() {
  return <aside className="ai-panel" id="ai-workspace">
    <div className="ai-panel-head"><span className="spark" aria-hidden="true">✦</span><span>AI WORKSPACE</span><AiStatus enabled={false} /></div>
    <div className="ai-overview">
      <h3>Ask more of your news.</h3>
      <p className="ai-intro">AI is off right now. Search and publisher results still work normally.</p>
    </div>
    <div className="ai-controls disabled-controls">
      <div className="ai-feature"><div><strong>Daily brief</strong><p>Pull distinct developments into one concise view.</p></div></div>
      <div className="ai-feature"><div><strong>Ask the news</strong><p>Question only the articles already in your feed.</p></div></div>
      <div className="ai-feature"><div><strong>Compare coverage</strong><p>Compare publishers only when the feed contains comparable coverage.</p></div></div>
    </div>
    <div className="ai-foundation"><span aria-hidden="true">✓</span><p><strong>Graceful fallback</strong><br />AI insights are temporarily unavailable. Please try again later.</p></div>
  </aside>;
}

function cleanAiText(text) {
  return (text || "")
    .replace(/\*\*/g, "")
    .replace(/\*/g, "")
    .replace(/^#{1,6}\s+/gm, "")
    .trim();
}

function StructuredInsight({ response }) {
  const citationMap = useMemo(
    () => new Map((response?.citations || []).map((citation) => [citation.id, citation])),
    [response]
  );

  if (!response?.content?.sections?.length) {
    return <div className="ai-insight-text">{cleanAiText(response?.text || "No AI response returned.")}</div>;
  }

  return <div className="ai-structured-insight">
    {response.content.sections.map((section, sectionIndex) => <section className="ai-response-section" key={`${section.heading}-${sectionIndex}`}>
      <h4>{section.heading}</h4>
      <ul>
        {(section.items || []).map((item, itemIndex) => <li key={`${item.text}-${itemIndex}`}>
          <span>{item.text}</span>
          {!!item.sourceIds?.length && <span className="ai-inline-sources" aria-label="Sources">
            {item.sourceIds.map((sourceId) => {
              const citation = citationMap.get(sourceId);
              if (!citation?.url || citation.url === "Unavailable") return null;
              return <a key={sourceId} href={citation.url} target="_blank" rel="noreferrer" title={citation.title}>
                [{sourceId}] {citation.source || `Source ${sourceId}`}
              </a>;
            })}
          </span>}
        </li>)}
      </ul>
    </section>)}

  </div>;
}

export default function AiWorkspace({ articles }) {
  const [question, setQuestion] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [enabled, setEnabled] = useState(false);
  const [activeLabel, setActiveLabel] = useState("");
  const resultRef = useRef(null);
  const pending = useRef(null);
  const version = useRef(0);

  useEffect(() => {
    version.current += 1;
    pending.current?.abort();
    pending.current = null;
    setLoading(false);
    setResult(null);
    setError("");
    setActiveLabel("");
    return () => {
      version.current += 1;
      pending.current?.abort();
      pending.current = null;
    };
  }, [articles]);

  useEffect(() => {
    let active = true;
    aiApi.status()
      .then((response) => active && setEnabled(Boolean(response.enabled)))
      .catch(() => active && setEnabled(false));
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if ((loading || result || error) && resultRef.current) {
      resultRef.current.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
  }, [loading, result, error]);

  const run = async (label, action, { clearQuestion = false } = {}) => {
    if (!enabled || !articles?.length || pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    const requestVersion = ++version.current;
    setActiveLabel(label);
    setResult(null);
    setLoading(true);
    setError("");
    try {
      const response = await action(controller.signal);
      if (requestVersion !== version.current) return;
      setResult(response || { text: "No AI response returned." });
      if (clearQuestion) setQuestion("");
    } catch (requestError) {
      if (requestVersion !== version.current || controller.signal.aborted) return;
      setError(aiErrorMessage(requestError));
    } finally {
      if (requestVersion === version.current) {
        pending.current = null;
        setLoading(false);
      }
    }
  };

  const ask = () => {
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion) return;
    run("Ask the news", (signal) => aiApi.ask(trimmedQuestion, articles, signal), { clearQuestion: true });
  };

  if (!enabled) return <DisabledAiWorkspace />;

  return <aside className="ai-panel" id="ai-workspace">
    <div className="ai-panel-head"><span className="spark" aria-hidden="true">✦</span><span>AI WORKSPACE</span><AiStatus enabled /></div>

    <div className="ai-overview">
      <h3>Ask more of your news.</h3>
      <p className="ai-intro">Brief, question, and compare only the stories currently shown in your feed.</p>
    </div>

    <div className="ai-controls">
      <div className="ai-feature ai-action">
        <div><strong>Daily brief</strong><p>Pull distinct developments into one concise view.</p><button type="button" disabled={loading || !articles?.length} onClick={() => run("Daily brief", (signal) => aiApi.brief(articles, signal))}>Create brief</button></div>
      </div>

      <div className="ai-feature ai-action">
        <div><strong>Ask the news</strong><p>Ask a question grounded in the current articles.</p><div className="ai-question"><input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="What matters most today?" onKeyDown={(event) => event.key === "Enter" && ask()} /><button type="button" disabled={loading || !question.trim()} onClick={ask}>Ask</button></div></div>
      </div>

      <div className="ai-feature ai-action">
        <div><strong>Compare coverage</strong><p>Compare observable emphasis only when stories overlap.</p><button type="button" disabled={loading || !articles?.length} onClick={() => run("Compare coverage", (signal) => aiApi.compare(articles, signal))}>Compare</button></div>
      </div>
    </div>

    {(loading || result || error) && <section className={`ai-insight-card ${loading ? "loading" : ""}`} ref={resultRef} aria-live="polite">
      <div className="ai-insight-head">
        <div><span className="ai-insight-kicker">✦ AI INSIGHT</span><strong>{activeLabel || "News intelligence"}</strong></div>
        {!loading && !error && <span className="ai-grounded-badge">Current feed only</span>}
      </div>
      {loading ? <div className="ai-thinking"><span /><span /><span /><p>Analyzing the current stories…</p></div> : error ? <p className="ai-error">{error}</p> : <StructuredInsight response={result} />}
    </section>}
  </aside>;
}
