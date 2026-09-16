import { useEffect, useState } from "react";

export default function SearchForm({ initialKeyword, initialPageSize, onSearch, loading }) {
  const [keyword, setKeyword] = useState(initialKeyword);
  const [pageSize, setPageSize] = useState(initialPageSize);

  useEffect(() => setKeyword(initialKeyword), [initialKeyword]);
  useEffect(() => setPageSize(initialPageSize), [initialPageSize]);

  function handleSubmit(event) {
    event.preventDefault();
    const cleanKeyword = keyword.trim();
    if (cleanKeyword) onSearch(cleanKeyword, pageSize);
  }

  return <form className={`search-form ${loading ? "is-searching" : ""}`} onSubmit={handleSubmit}>
    <label className="search-field" htmlFor="news-search">
      <span className="search-icon" aria-hidden="true">⌕</span>
      <span className="sr-only">Search news</span>
      <input id="news-search" value={keyword} onChange={(event) => setKeyword(event.target.value)} maxLength="120" placeholder="Search a topic, company, place or person" autoComplete="off" />
    </label>
    <label className="page-size" htmlFor="page-size">
      <span className="sr-only">Stories per page</span>
      <select id="page-size" value={pageSize} onChange={(event) => setPageSize(Number(event.target.value))}>
        <option value={8}>8 stories</option>
        <option value={12}>12 stories</option>
        <option value={20}>20 stories</option>
      </select>
    </label>
    <button type="submit" disabled={loading || !keyword.trim()}>{loading ? <><i className="button-loader" />Searching</> : <>Search news <span aria-hidden="true">→</span></>}</button>
  </form>;
}
