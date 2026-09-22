import { useState } from 'react';

const stages = [
  { title: 'Reporting', subtitle: 'Guardian + NYT', icon: '▤', detail: 'Search reporting from The Guardian and The New York Times. Open any story to read it at its original source.' },
  { title: 'Your news feed', subtitle: 'One place to explore', icon: '≋', detail: 'Stories are combined, duplicates are removed, and your feed is saved briefly so you can move between pages.' },
  { title: 'AI insight', subtitle: 'Gemini + source links', icon: '✦', detail: 'Gemini uses up to eight headlines and excerpts from your page to create a brief, answer a question, or compare coverage. Answers link back to their sources.' },
];

export default function NewsFlow() {
  const [active, setActive] = useState(null);
  return <section className="news-flow" aria-label="How news becomes an insight">
    <div className="news-flow-heading"><span>FROM REPORTING TO PERSPECTIVE</span><small>Explore the flow</small></div>
    <div className="news-flow-stage">
      {stages.map((stage, index) => <button key={stage.title} type="button" className={`news-flow-node ${active === index ? 'selected' : ''}`} aria-expanded={active === index} aria-controls="news-flow-detail" onClick={() => setActive(active === index ? null : index)}>
        <span className="news-flow-icon" aria-hidden="true">{stage.icon}</span>
        <strong>{stage.title}</strong><small>{stage.subtitle}</small>
        {index < stages.length - 1 && <span className="news-flow-arrow" aria-hidden="true">→</span>}
      </button>)}
    </div>
    <p id="news-flow-detail" className="news-flow-detail" hidden={active === null} aria-live="polite">{active !== null ? stages[active].detail : ''}</p>
  </section>;
}
