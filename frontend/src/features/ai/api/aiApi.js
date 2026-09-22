import axios from 'axios';
const client = axios.create({ baseURL: '/api/ai', timeout: 60000, headers: { Accept: 'application/json' } });
export default {
  async status(signal) { return (await client.get('/status', { signal, timeout: 90000 })).data; },
  async brief(selection, signal) { return (await client.post('/brief', selection, { signal })).data; },
  async compare(selection, signal) { return (await client.post('/compare', selection, { signal })).data; },
  async ask(question, selection, signal) { return (await client.post('/ask', { question, selection }, { signal })).data; },
};
