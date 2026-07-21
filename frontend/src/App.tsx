import { FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bot,
  CircleAlert,
  Image as ImageIcon,
  Loader2,
  MessageCircle,
  PackageSearch,
  Paperclip,
  RefreshCw,
  Search,
  Send,
  ShoppingBag,
  User,
  X
} from 'lucide-react';
import { StorePage } from './StorePage';

type Role = 'user' | 'assistant';
type HealthState = 'checking' | 'online' | 'offline';

type RequestHistoryTurn = {
  role: Role;
  content: string;
};

type ImageResult = {
  id?: string;
  productId?: string;
  product_id?: string;
  skuId?: string;
  sku_id?: string;
  title?: string;
  imageUrl?: string;
  image_url?: string;
  link?: string;
  score?: number;
  metadata?: Record<string, unknown>;
};

type ProductResult = {
  id: number;
  code: string;
  name: string;
  subtitle: string;
  category: string;
  price: number;
  originalPrice: number;
  imageUrl: string;
  detailUrl: string;
  score?: number;
};

type Reference = {
  title?: string;
  content?: string;
  metadata?: Record<string, unknown>;
};

type ChatMessage = {
  id: string;
  role: Role;
  content: string;
  createdAt: string;
  imageResults?: ImageResult[];
  productResults?: ProductResult[];
  references?: Reference[];
  uploadPreviewUrl?: string;
};

type ApiResponse = {
  success?: boolean;
  content?: string;
  answer?: string;
  message?: string;
  error?: string;
  imageResults?: ImageResult[];
  image_results?: ImageResult[];
  productResults?: ProductResult[];
  sources?: Reference[];
  references?: Reference[];
};

const quickQuestions = [
  '\u60f3\u4e70\u4e00\u6b3e 1000 \u5143\u4ee5\u5185\u3001\u9002\u5408\u901a\u52e4\u7684\u964d\u566a\u8033\u673a',
  '\u5c55\u793a\u5316\u5986\u54c1\u5546\u54c1',
  '\u627e\u4e00\u4ef6\u7fbd\u7ed2\u670d',
  '\u6839\u636e\u6211\u4e0a\u4f20\u7684\u7167\u7247\u627e\u76f8\u4f3c\u5546\u54c1'
];

const MIN_VISIBLE_SCORE = 0.4;

const MAX_REQUEST_HISTORY = 8;

function createId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function getNowLabel() {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date());
}

function createWelcomeMessage(): ChatMessage {
  return {
    id: createId(),
    role: 'assistant',
    content:
      '\u6211\u53ef\u4ee5\u6839\u636e\u9884\u7b97\u3001\u4f7f\u7528\u573a\u666f\u548c\u529f\u80fd\u9700\u6c42\u4ece\u5546\u57ce\u4e2d\u63a8\u8350\u5546\u54c1\u3002\u70b9\u51fb\u63a8\u8350\u5361\u7247\u53ef\u67e5\u770b\u5b9e\u65f6\u4ef7\u683c\u3001\u89c4\u683c\u548c\u5e93\u5b58\u3002',
    createdAt: getNowLabel()
  };
}

function toRequestHistory(messages: ChatMessage[]): RequestHistoryTurn[] {
  return messages
    .filter((message) => message.role === 'user' || message.role === 'assistant')
    .filter((message) => message.content.trim())
    .slice(-MAX_REQUEST_HISTORY)
    .map((message) => ({
      role: message.role,
      content: message.content
    }));
}

async function parseApiError(response: Response) {
  try {
    const data = (await response.json()) as ApiResponse;
    return data.message || data.error || `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
}

function imageUrlForDisplay(result: ImageResult) {
  const raw = result.imageUrl || result.image_url || '';
  if (!raw) {
    return '';
  }
  if (/^https?:\/\//i.test(raw) || raw.startsWith('/')) {
    return raw;
  }
  return `/api/mall/images/preview?path=${encodeURIComponent(raw)}`;
}

function productIdOf(result: ImageResult) {
  return result.productId || result.product_id || '-';
}

function visibleResults(results: ImageResult[]) {
  return results.filter((result) => typeof result.score !== 'number' || result.score >= MIN_VISIBLE_SCORE);
}

function formatProductPrice(price: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  }).format(price);
}

function buildComingSoonUrl(result: ImageResult) {
  const params = new URLSearchParams();
  const productId = productIdOf(result);
  if (result.title) {
    params.set('title', result.title);
  }
  if (productId && productId !== '-') {
    params.set('product_id', productId);
  }
  if (result.link) {
    params.set('from', result.link);
  }
  return `/coming-soon${params.toString() ? `?${params.toString()}` : ''}`;
}

function ComingSoonPage() {
  const params = new URLSearchParams(window.location.search);
  const title = params.get('title') || '\u5546\u54c1\u8be6\u60c5';
  const productId = params.get('product_id');

  return (
    <main className="coming-soon-page">
      <section className="coming-soon-panel">
        <div className="brand-mark" aria-hidden="true">
          <ShoppingBag size={28} />
        </div>
        <p className="eyebrow">{'\u5546\u57ce\u9884\u89c8'}</p>
        <h1>{'\u656c\u8bf7\u671f\u5f85'}</h1>
        <p className="coming-soon-copy">
          {title}
          {productId ? ` (${productId})` : ''}
          {'\u6682\u672a\u63a5\u5165\u771f\u5b9e\u5546\u54c1\u8be6\u60c5\u9875\u3002'}
        </p>
        <button className="secondary-button" type="button" onClick={() => window.history.back()}>
          {'\u8fd4\u56de'}
        </button>
      </section>
    </main>
  );
}

export function App() {
  const isComingSoonRoute = window.location.pathname === '/coming-soon';
  const isStoreRoute = window.location.pathname === '/mall'
    || window.location.pathname.startsWith('/mall/');
  const [messages, setMessages] = useState<ChatMessage[]>([createWelcomeMessage()]);
  const [question, setQuestion] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [health, setHealth] = useState<HealthState>('checking');
  const [isAsking, setIsAsking] = useState(false);
  const [error, setError] = useState('');
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const composerRef = useRef<HTMLTextAreaElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const healthLabel = useMemo(() => {
    if (health === 'online') {
      return '\u540e\u7aef\u5728\u7ebf';
    }
    if (health === 'offline') {
      return '\u540e\u7aef\u79bb\u7ebf';
    }
    return '\u8fde\u63a5\u4e2d';
  }, [health]);

  async function checkHealth() {
    setHealth('checking');
    try {
      const response = await fetch('/api/health');
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      setHealth('online');
    } catch {
      setHealth('offline');
    }
  }

  useEffect(() => {
    if (!isComingSoonRoute && !isStoreRoute) {
      void checkHealth();
    }
  }, [isComingSoonRoute, isStoreRoute]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, isAsking]);

  async function askQuestion(rawQuestion: string, file: File | null = selectedFile) {
    const content = rawQuestion.trim();
    if ((!content && !file) || isAsking) {
      return;
    }

    const previousMessages = messages;
    const uploadPreviewUrl = file ? URL.createObjectURL(file) : undefined;
    const userMessage: ChatMessage = {
      id: createId(),
      role: 'user',
      content: content || '\u6839\u636e\u8fd9\u5f20\u7167\u7247\u627e\u76f8\u4f3c\u5546\u54c1',
      createdAt: getNowLabel(),
      uploadPreviewUrl
    };

    setError('');
    setQuestion('');
    setSelectedFile(null);
    setIsAsking(true);
    setMessages((current) => [...current, userMessage]);

    try {
      const response = file
        ? await askWithImage(content, file)
        : await fetch('/api/mall/chat', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              message: content,
              topK: 5,
              history: toRequestHistory(previousMessages)
            })
          });

      if (!response.ok) {
        throw new Error(await parseApiError(response));
      }

      const data = (await response.json()) as ApiResponse;
      if (data.success === false) {
        throw new Error(data.message || data.error || '\u8bf7\u6c42\u5931\u8d25');
      }

      setMessages((current) => [
        ...current,
        {
          id: createId(),
          role: 'assistant',
          content: data.content || data.answer || '\u6682\u65f6\u6ca1\u6709\u8fd4\u56de\u56de\u7b54\u3002',
          imageResults: visibleResults(data.imageResults || data.image_results || []),
          productResults: data.productResults || [],
          references: data.references || data.sources || [],
          createdAt: getNowLabel()
        }
      ]);
    } catch (err) {
      const message = err instanceof Error ? err.message : '\u8bf7\u6c42\u5931\u8d25';
      setError(message);
      setMessages((current) => [
        ...current,
        {
          id: createId(),
          role: 'assistant',
          content: `\u540e\u7aef\u9519\u8bef\uff1a${message}`,
          createdAt: getNowLabel()
        }
      ]);
    } finally {
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      setIsAsking(false);
      window.requestAnimationFrame(() => composerRef.current?.focus());
    }
  }

  async function askWithImage(content: string, file: File) {
    const formData = new FormData();
    formData.set('message', content || '\u6839\u636e\u8fd9\u5f20\u7167\u7247\u627e\u76f8\u4f3c\u5546\u54c1');
    formData.set('topK', '5');
    formData.set('file', file);
    return fetch('/api/mall/chat/with-image', {
      method: 'POST',
      body: formData
    });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void askQuestion(question);
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void askQuestion(question);
    }
  }

  function resetChat() {
    setError('');
    setQuestion('');
    setSelectedFile(null);
    setMessages([createWelcomeMessage()]);
    window.requestAnimationFrame(() => composerRef.current?.focus());
  }

  if (isComingSoonRoute) {
    return <ComingSoonPage />;
  }

  if (isStoreRoute) {
    return <StorePage />;
  }

  return (
    <main className="app-shell">
      <header className="global-nav">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">
            <ShoppingBag size={24} strokeWidth={2} />
          </div>
          <div>
            <h1>{'\u5546\u57ce\u667a\u80fd\u52a9\u624b'}</h1>
            <span>{'\u652f\u6301\u6587\u672c\u68c0\u7d22\u3001\u7167\u7247\u68c0\u7d22\u548c\u5546\u54c1\u63a8\u8350'}</span>
          </div>
        </div>

        <div className="nav-actions">
          <a className="mode-switch-link" href="/mall">
            <ShoppingBag size={17} aria-hidden="true" />
            <span>{'商品商城'}</span>
          </a>
          <div className={`status-pill ${health}`} aria-live="polite">
            <span className="status-dot" />
            <span>{healthLabel}</span>
          </div>
          <button className="icon-button" type="button" onClick={checkHealth} title="\u5237\u65b0\u540e\u7aef\u72b6\u6001">
            <RefreshCw size={18} aria-hidden="true" strokeWidth={2} />
          </button>
          <button className="new-session-button" type="button" onClick={resetChat}>
            {'\u65b0\u4f1a\u8bdd'}
          </button>
        </div>
      </header>

      <section className="workspace">
        <aside className="support-panel" aria-label="\u52a9\u624b\u5de5\u5177">
          <section className="side-welcome">
            <div className="assistant-avatar" aria-hidden="true">
              <Bot size={21} strokeWidth={2} />
            </div>
            <div>
              <h2>{'\u8bd5\u4e00\u4e0b'}</h2>
              <p>{'\u76f4\u63a5\u63cf\u8ff0\u60f3\u627e\u7684\u5546\u54c1\uff0c\u6216\u4e0a\u4f20\u4e00\u5f20\u7167\u7247\u67e5\u627e\u76f8\u4f3c\u5546\u54c1\u3002'}</p>
            </div>
          </section>

          <section className="side-section">
            <div className="section-heading">
              <span>{'\u5feb\u901f\u6d4b\u8bd5'}</span>
            </div>
            <div className="scenario-grid visual-tests">
              {quickQuestions.map((item) => (
                <button type="button" key={item} onClick={() => void askQuestion(item)} disabled={isAsking}>
                  <Search size={16} aria-hidden="true" strokeWidth={2} />
                  <span>{item}</span>
                </button>
              ))}
            </div>
          </section>

          <section className="side-section service-list">
            <article className="service-card blue">
              <div className="service-icon" aria-hidden="true">
                <ImageIcon size={18} strokeWidth={2} />
              </div>
              <div>
                <h3>{'\u6587\u672c\u627e\u5546\u54c1'}</h3>
                <p>{'\u53ef\u4ee5\u627e\u624b\u673a\u3001\u8863\u670d\u3001\u5316\u5986\u54c1\u3001\u88d9\u5b50\u6216\u7fbd\u7ed2\u670d\u3002'}</p>
              </div>
            </article>
            <article className="service-card orange">
              <div className="service-icon" aria-hidden="true">
                <PackageSearch size={18} strokeWidth={2} />
              </div>
              <div>
                <h3>{'\u7167\u7247\u627e\u540c\u6b3e'}</h3>
                <p>{'\u4e0a\u4f20\u4e00\u5f20\u5546\u54c1\u7167\uff0c\u5728 Milvus \u4e2d\u67e5\u627e\u76f8\u4f3c\u5546\u54c1\u3002'}</p>
              </div>
            </article>
          </section>
        </aside>

        <section className="chat-panel" aria-label="\u5546\u57ce\u89c6\u89c9\u5bf9\u8bdd">
          <div className="session-bar">
            <div>
              <span>{'\u5f53\u524d\u6a21\u5f0f'}</span>
              <strong>{'\u5546\u54c1\u5411\u91cf\u68c0\u7d22'}</strong>
            </div>
            <code>/api/mall/chat</code>
          </div>

          <div className="recommendation-grid" aria-label="\u63a8\u8350\u95ee\u6cd5">
            {quickQuestions.map((item, index) => (
              <button
                type="button"
                className="recommendation-card"
                key={item}
                onClick={() => void askQuestion(item)}
              disabled={isAsking}
            >
                {index === 0 && <span className="hot-badge">{'\u6d4b\u8bd5'}</span>}
                <MessageCircle size={17} aria-hidden="true" strokeWidth={2} />
                <span>{item}</span>
              </button>
            ))}
          </div>

          {error && (
            <div className="error-banner" role="status">
              <CircleAlert size={18} aria-hidden="true" strokeWidth={2} />
              <span>{error}</span>
            </div>
          )}

          <div className="messages" aria-live="polite">
            {messages.map((message) => (
              <article key={message.id} className={`message ${message.role}`}>
                <div className="avatar" aria-hidden="true">
                  {message.role === 'assistant' ? <Bot size={18} strokeWidth={2} /> : <User size={18} strokeWidth={2} />}
                </div>
                <div className="bubble">
                  <div className="message-meta">
                    <span>{message.role === 'assistant' ? '\u52a9\u624b' : '\u6211'}</span>
                    <span>{message.createdAt}</span>
                    {message.imageResults && message.imageResults.length > 0 && (
                      <span className="source-badge">
                        <ImageIcon size={12} aria-hidden="true" strokeWidth={2} />
                        {'\u5546\u54c1\u7ed3\u679c'}
                      </span>
                    )}
                    {message.productResults && message.productResults.length > 0 && (
                      <span className="source-badge product-source-badge">
                        <ShoppingBag size={12} aria-hidden="true" strokeWidth={2} />
                        {'\u5546\u57ce\u5728\u552e\u5546\u54c1'}
                      </span>
                    )}
                  </div>
                  <p>{message.content}</p>

                  {message.uploadPreviewUrl && (
                    <div className="upload-preview">
                      <img src={message.uploadPreviewUrl} alt="\u4e0a\u4f20\u7684\u67e5\u8be2\u7167\u7247" />
                    </div>
                  )}

                  {message.imageResults && message.imageResults.length > 0 && (
                    <div className="image-result-grid" aria-label="\u5546\u54c1\u68c0\u7d22\u7ed3\u679c">
                      {message.imageResults.map((result, index) => (
                        <a className="image-result-card" href={buildComingSoonUrl(result)} key={`${message.id}-${index}`}>
                          <img src={imageUrlForDisplay(result)} alt={result.title || `\u5546\u54c1 ${index + 1}`} loading="lazy" />
                          <div className="image-result-body">
                            <strong>{result.title || `\u5546\u54c1 ${index + 1}`}</strong>
                            <span>{'\u5546\u54c1ID\uff1a'}{productIdOf(result)}</span>
                          </div>
                        </a>
                      ))}
                    </div>
                  )}

                  {message.productResults && message.productResults.length > 0 && (
                    <div className="product-result-grid" aria-label="\u5546\u54c1\u63a8\u8350\u7ed3\u679c">
                      {message.productResults.map((product) => (
                        <a className="product-result-card" href={product.detailUrl} key={`${message.id}-${product.id}`}>
                          <img src={product.imageUrl} alt={product.name} loading="lazy" />
                          <div className="product-result-body">
                            <span>{product.category}</span>
                            <strong>{product.name}</strong>
                            <p>{product.subtitle}</p>
                            <div>
                              <strong>{formatProductPrice(product.price)}</strong>
                              {product.originalPrice > product.price && (
                                <del>{formatProductPrice(product.originalPrice)}</del>
                              )}
                            </div>
                          </div>
                        </a>
                      ))}
                    </div>
                  )}

                  {message.references && message.references.length > 0 && (
                    <div className="reference-list">
                      <div className="reference-title">
                        <PackageSearch size={15} aria-hidden="true" strokeWidth={2} />
                        <span>{'\u77e5\u8bc6\u6765\u6e90'}</span>
                      </div>
                      {message.references.slice(0, 4).map((reference, index) => (
                        <article className="reference-card disabled" key={`${message.id}-ref-${index}`}>
                          <div>
                            <strong>{reference.title || `\u6765\u6e90 ${index + 1}`}</strong>
                          </div>
                          {reference.content && <p>{reference.content}</p>}
                        </article>
                      ))}
                    </div>
                  )}
                </div>
              </article>
            ))}

            {isAsking && (
              <article className="message assistant">
                <div className="avatar" aria-hidden="true">
                  <Bot size={18} strokeWidth={2} />
                </div>
                <div className="bubble loading-bubble">
                  <Loader2 size={18} aria-hidden="true" />
                  <span>{'\u6b63\u5728\u68c0\u7d22\u5546\u54c1\u5411\u91cf...'}</span>
                </div>
              </article>
            )}
            <div ref={messagesEndRef} />
          </div>

          <div className="composer-shell">
            {selectedFile && (
              <div className="selected-file">
                <Paperclip size={16} aria-hidden="true" />
                <span>{selectedFile.name}</span>
                <button type="button" onClick={() => setSelectedFile(null)} title="\u79fb\u9664\u7167\u7247">
                  <X size={15} aria-hidden="true" />
                </button>
              </div>
            )}
            <form className="composer visual-composer" onSubmit={handleSubmit}>
              <button
                className="attach-button"
                type="button"
                onClick={() => fileInputRef.current?.click()}
                title="\u4e0a\u4f20\u7167\u7247"
                disabled={isAsking}
              >
                <Paperclip size={19} aria-hidden="true" />
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                hidden
                onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
              />
              <textarea
                ref={composerRef}
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={handleComposerKeyDown}
                placeholder="\u8bd5\u8bd5\uff1a\u7ed9\u6211\u627e\u4e00\u6b3e\u624b\u673a\uff0c\u6216\u4e0a\u4f20\u7167\u7247\u67e5\u627e\u76f8\u4f3c\u5546\u54c1"
                rows={2}
              />
              <button className="send-button" type="submit" disabled={(!question.trim() && !selectedFile) || isAsking}>
                {isAsking ? <Loader2 size={18} aria-hidden="true" /> : <Send size={18} aria-hidden="true" />}
                <span>{'\u53d1\u9001'}</span>
              </button>
            </form>
          </div>
        </section>
      </section>
    </main>
  );
}
