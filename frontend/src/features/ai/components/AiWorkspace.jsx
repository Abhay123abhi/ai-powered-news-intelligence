import { useEffect, useMemo, useRef, useState } from 'react';
import aiApi from '../api/aiApi';

const LABELS = { checking: 'Checking availability', ready: 'Ready', generating: 'Generating', quota: 'Quota reached', unavailable: 'Temporarily unavailable', disabled: 'Unavailable' };
const EXAMPLE = { example: true, content: { sections: [{ heading: 'Example briefing', items: [
  { text: 'A city announces an electric-bus trial. A useful brief states the reported change and links to the original source.', sourceIds: [] },
  { text: 'If the excerpts do not explain costs or results, the answer says there is not enough evidence.', sourceIds: [] },
] }] }, citations: [] };
const safeUrl = (url) => { try { const parsed = new URL(url); return parsed.protocol === 'https:' ? parsed.href : null; } catch { return null; } };

export function StructuredInsight({ response }) {
  const citations = new Map((response?.citations || []).map(c => [c.id, c]));
  return <div className="ai-structured-insight">
    {(response?.content?.sections || []).map((section, index) => <section className="ai-response-section" key={index}>
      <h4>{section.heading}</h4><ul>{section.items.map((item, i) => <li key={i}><span>{item.text}</span>
        {!!item.sourceIds?.length && <span className="ai-inline-sources" aria-label="Sources">{item.sourceIds.map(id => {
          const citation = citations.get(id); const url = safeUrl(citation?.url);
          return url ? <a key={id} href={url} target="_blank" rel="noopener noreferrer" title={citation.title}>[{id}] {citation.source}</a> : null;
        })}</span>}
      </li>)}</ul>
    </section>)}
  </div>;
}

export default function AiWorkspace({ articles, feedId, page = 1, feedLoading = false, onRefresh }) {
  const [question, setQuestion] = useState('');
  const [result, setResult] = useState(null);
  const [status, setStatus] = useState('checking');
  const [configured, setConfigured] = useState(false);
  const [error, setError] = useState('');
  const [askedQuestion, setAskedQuestion] = useState('');
  const [activeLabel, setActiveLabel] = useState('');
  const [selected, setSelected] = useState([]);
  const [retryAt, setRetryAt] = useState(null);
  const [expired, setExpired] = useState(false);
  const inFlight = useRef(false);
  const request = useRef(null);
  const sequence = useRef(0);
  const resultRef = useRef(null);
  const feedKey = useMemo(() => `${feedId || ''}:${page}:${articles.map(a => a.url).join('|')}`, [feedId, page, articles]);

  useEffect(() => {
    const controller = new AbortController();
    aiApi.status(controller.signal).then(data => {
      setConfigured(Boolean(data.enabled));
      setStatus(data.enabled ? 'ready' : 'disabled');
    }).catch(error => { if (!controller.signal.aborted) setStatus('unavailable'); });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    sequence.current += 1;
    request.current?.abort();
    inFlight.current = false;
    setResult(null); setError(''); setActiveLabel(''); setExpired(false);
    setSelected(articles.slice(0, 8).map((_, i) => i));
    setStatus(value => value === 'generating' ? 'ready' : value);
    return () => { sequence.current += 1; request.current?.abort(); inFlight.current = false; };
    // Article objects can change without changing the actual feed; identity is explicit above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [feedKey]);

  useEffect(() => {
    if (feedLoading) {
      sequence.current += 1; request.current?.abort(); inFlight.current = false;
      setResult(null); setError(''); setStatus(value => value === 'generating' ? 'ready' : value);
    }
  }, [feedLoading]);

  useEffect(() => {
    if (!result && !error) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    resultRef.current?.scrollIntoView?.({ behavior: reduced ? 'auto' : 'smooth', block: 'nearest' });
  }, [result, error]);

  const loading = status === 'generating';
  const canRun = configured && feedId && selected.length > 0 && !feedLoading && !expired;
  const sources = new Set(selected.map(i => articles[i]?.source).filter(Boolean));
  const selection = { feedId, page, articleIds: selected };

  async function checkAgain() {
    if (status === 'checking' || inFlight.current) return;
    setStatus('checking'); setError('');
    try { const data = await aiApi.status(); setConfigured(Boolean(data.enabled)); setStatus(data.enabled ? 'ready' : 'disabled'); }
    catch { setStatus('unavailable'); setError('Availability could not be checked. News browsing still works.'); }
  }

  async function run(label, action) {
    if (!canRun || inFlight.current) return;
    inFlight.current = true;
    const version = ++sequence.current;
    const controller = new AbortController(); request.current = controller;
    setActiveLabel(label); setAskedQuestion(label === 'Ask the news' ? question.trim() : ''); setStatus('generating'); setError(''); setResult(null);
    try {
      const data = await action(controller.signal);
      if (version !== sequence.current) return;
      setResult(data); setStatus('ready'); setRetryAt(null);
    } catch (failure) {
      if (controller.signal.aborted || version !== sequence.current) return;
      const body = failure.response?.data;
      const code = body?.code;
      const quota = code === 'AI_QUOTA_REACHED';
      setStatus(quota ? 'quota' : 'unavailable');
      setExpired(failure.response?.status === 410);
      setRetryAt(body?.retryAfter ? new Date(Date.now() + body.retryAfter * 1000) : null);
      setError(body?.detail || 'AI could not complete this request. Please try again later; the news is still available.');
    } finally { if (version === sequence.current) inFlight.current = false; }
  }

  function toggleStory(index) {
    if (inFlight.current) return;
    setSelected(current => current.includes(index) ? current.filter(i => i !== index) : [...current, index].sort((a, b) => a - b));
    setResult(null); setError('');
  }
  function ask(event) {
    event.preventDefault();
    if (!question.trim()) return;
    run('Ask the news', signal => aiApi.ask(question.trim(), selection, signal));
  }

  return <aside className="ai-panel" id="ai-workspace" aria-label="AI workspace" aria-busy={loading}>
    <div className="ai-panel-head"><span className="spark" aria-hidden="true">✦</span><span>AI WORKSPACE</span>
      <small className={`ai-status ${status}`} role="status"><span aria-hidden="true">●</span> {LABELS[status]}</small>
    </div>
    <div className="ai-overview"><h3>Ask more of your news.</h3>
      <p className="ai-intro">Explore selected headlines and excerpts, with links to the reporting behind them.</p>
    </div>
    {status === 'checking' && <p className="ai-availability-note">Connecting to the shared AI workspace. The first visit can take a little longer.</p>}
    {!configured && status !== 'checking' && <div className="ai-availability-note"><p>AI insights are taking a break. You can still search and read the original reporting.</p><button type="button" onClick={checkAgain}>Check availability</button></div>}
    <details className="ai-story-selection">
      <summary>Based on {selected.length} of {articles.length} stories · Choose stories</summary>
      <p id="story-selection-help">Choose up to 8 stories. For comparisons, select the same topic from both publishers.</p>
      <div className="ai-selection-list">{articles.map((article, index) => <label key={`${article.url}-${index}`}>
        <input type="checkbox" checked={selected.includes(index)} disabled={loading || feedLoading || (!selected.includes(index) && selected.length >= 8)} onChange={() => toggleStory(index)} />
        <span>{article.title}<small>{article.source}</small></span>
      </label>)}</div>
    </details>
    <div className="ai-controls">
      <div className="ai-feature ai-action"><div><strong>Daily brief</strong><p>The key developments in your selection.</p>
        <button type="button" disabled={loading || !canRun} onClick={() => run('Daily brief', signal => aiApi.brief(selection, signal))}>Create brief</button>
      </div></div>
      <div className="ai-feature ai-action"><div><strong>Compare coverage</strong><p>{sources.size < 2 ? 'Choose stories from both publishers.' : 'Compare reporting on the same topic.'}</p>
        <button type="button" disabled={loading || !canRun || sources.size < 2} onClick={() => run('Compare coverage', signal => aiApi.compare(selection, signal))}>Compare</button>
      </div></div>
      <form className="ai-feature ai-action ai-ask-row" onSubmit={ask}>
        <label htmlFor="news-question">Ask the news</label><p id="question-help">Answers use only your selected excerpts.</p>
        <div className="ai-question"><input id="news-question" disabled={loading || feedLoading} value={question} maxLength={500} aria-describedby="question-help" onChange={event => setQuestion(event.target.value)} placeholder="What changed, and why does it matter?" />
          <button type="submit" disabled={loading || !canRun || !question.trim()}>Ask</button></div>
      </form>
    </div>
    <p className="ai-evidence-note">AI can make mistakes. Check the linked sources. Please don’t enter personal or sensitive information.</p>
    <button className="ai-example-button" type="button" disabled={loading} onClick={() => { setResult(EXAMPLE); setError(''); setActiveLabel('Illustrative example'); }}>View an example · no AI request</button>
    {(loading || result || error) && <section className={`ai-insight-card ${loading ? 'loading' : ''}`} ref={resultRef} aria-live="polite">
      <div className="ai-insight-head"><div><span className="ai-insight-kicker">✦ AI INSIGHT</span><strong>{activeLabel}</strong></div>
        {result && <span className="ai-grounded-badge">{result.example ? 'Example, not live news' : result.cached ? 'Cached insight' : `${selected.length} selected stories`}</span>}
      </div>
      {activeLabel === 'Ask the news' && askedQuestion && <p className="ai-asked-question">Your question: {askedQuestion}</p>}
      {loading ? <div className="ai-thinking"><span /><span /><span /><p>Reading your selected excerpts…</p></div> : error ? <div className="ai-error"><p>{error}</p>
        {retryAt && <p>Try new insights after {retryAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}. An identical cached request may still be available.</p>}
        {expired && <button type="button" onClick={onRefresh}>Refresh news</button>}
      </div> : <StructuredInsight response={result} />}
    </section>}
  </aside>;
}
