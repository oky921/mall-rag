import { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  Bot,
  Check,
  ChevronRight,
  CircleCheck,
  Clock3,
  CreditCard,
  Home,
  Loader2,
  LogIn,
  LogOut,
  MapPin,
  Minus,
  Package,
  Pencil,
  Plus,
  ReceiptText,
  RotateCcw,
  Search,
  ShoppingBag,
  ShoppingCart,
  Star,
  Store,
  Trash2,
  User,
  X
} from 'lucide-react';

type Product = {
  id: number;
  code: string;
  name: string;
  subtitle: string;
  description: string;
  category: string;
  price: number;
  originalPrice: number;
  stock: number;
  sales: number;
  rating: number;
  imageUrl: string;
  featured: boolean;
};

type Sku = {
  id: number;
  skuCode: string;
  specValues: Record<string, string>;
  price: number;
  originalPrice: number;
  stock: number;
  sales: number;
  imageUrl: string;
  enabled: boolean;
};

type ProductDetail = { product: Product; skus: Sku[] };
type Category = { name: string; count: number };
type ProductListResponse = { items: Product[]; total: number };

type CartItem = {
  id: number;
  skuId: number;
  productId: number;
  productName: string;
  skuCode: string;
  specValues: Record<string, string>;
  price: number;
  originalPrice: number;
  stock: number;
  imageUrl: string;
  quantity: number;
  subtotal: number;
  enabled: boolean;
};

type CartResponse = { items: CartItem[]; totalQuantity: number; totalAmount: number };

type UserProfile = {
  id: number;
  username: string;
  displayName: string;
  phone?: string | null;
  createdAt: string;
};

type Address = {
  id: number;
  receiverName: string;
  receiverPhone: string;
  province?: string | null;
  city?: string | null;
  district?: string | null;
  detailAddress: string;
  postalCode?: string | null;
  defaultAddress: boolean;
  fullAddress: string;
};

type AddressDraft = Omit<Address, 'id' | 'fullAddress'>;

type OrderItem = {
  id: number;
  productId: number;
  skuId: number;
  productName: string;
  skuCode: string;
  specValues: Record<string, string>;
  price: number;
  quantity: number;
  subtotal: number;
  imageUrl: string;
};

type OrderResponse = {
  id: number;
  orderNo: string;
  status: string;
  totalAmount: number;
  receiverName: string;
  receiverPhone: string;
  receiverAddress: string;
  createdAt: string;
  updatedAt: string;
  paymentNo?: string | null;
  paidAt?: string | null;
  cancelledAt?: string | null;
  items: OrderItem[];
};

type MallRoute =
  | { kind: 'list' }
  | { kind: 'detail'; productId: number }
  | { kind: 'checkout' }
  | { kind: 'orders' }
  | { kind: 'order'; orderId: number }
  | { kind: 'login' }
  | { kind: 'account' }
  | { kind: 'addresses' };

const emptyCart: CartResponse = { items: [], totalQuantity: 0, totalAmount: 0 };

function currency(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: 'CNY',
    minimumFractionDigits: 0
  }).format(value);
}

function specLabel(specs: Record<string, string>) {
  return Object.entries(specs).map(([key, value]) => `${key}：${value}`).join(' · ');
}

function currentRoute(): MallRoute {
  const detail = window.location.pathname.match(/^\/mall\/products\/(\d+)\/?$/);
  if (detail) return { kind: 'detail', productId: Number(detail[1]) };
  const order = window.location.pathname.match(/^\/mall\/orders\/(\d+)\/?$/);
  if (order) return { kind: 'order', orderId: Number(order[1]) };
  if (window.location.pathname === '/mall/orders') return { kind: 'orders' };
  if (window.location.pathname === '/mall/checkout') return { kind: 'checkout' };
  if (window.location.pathname === '/mall/login') return { kind: 'login' };
  if (window.location.pathname === '/mall/account/addresses') return { kind: 'addresses' };
  if (window.location.pathname === '/mall/account') return { kind: 'account' };
  return { kind: 'list' };
}

class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

let csrfHeader = 'X-XSRF-TOKEN';
let csrfToken = '';

async function ensureCsrf() {
  if (csrfToken) return;
  const response = await fetch('/api/store/auth/csrf', { credentials: 'same-origin' });
  if (!response.ok) throw new ApiError(response.status, '安全校验初始化失败');
  const body = await response.json() as { headerName: string; token: string };
  csrfHeader = body.headerName;
  csrfToken = body.token;
}

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const method = (options?.method || 'GET').toUpperCase();
  const headers = new Headers(options?.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    await ensureCsrf();
    headers.set(csrfHeader, csrfToken);
  }
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
  if (response.ok) {
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }
  let message = `请求失败（${response.status}）`;
  try {
    const body = await response.json() as { detail?: string; message?: string; error?: string };
    message = body.detail || body.message || body.error || message;
  } catch {
    // Keep the status-based fallback when the response has no JSON body.
  }
  throw new ApiError(response.status, message);
}

export function StorePage() {
  const [route, setRoute] = useState<MallRoute>(currentRoute);
  const [cart, setCart] = useState<CartResponse>(emptyCart);
  const [cartOpen, setCartOpen] = useState(false);
  const [cartLoading, setCartLoading] = useState(true);
  const [cartBusy, setCartBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [user, setUser] = useState<UserProfile | null>();

  function navigate(path: string) {
    window.history.pushState({}, '', path);
    setRoute(currentRoute());
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function loginPath(next = window.location.pathname) {
    return `/mall/login?next=${encodeURIComponent(next)}`;
  }

  async function loadCart() {
    setCartLoading(true);
    try {
      setCart(await api<CartResponse>('/api/store/cart'));
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setUser(null);
        setCart(emptyCart);
        return;
      }
      setNotice(error instanceof Error ? error.message : '购物车加载失败');
    } finally {
      setCartLoading(false);
    }
  }

  useEffect(() => {
    const onPopState = () => setRoute(currentRoute());
    window.addEventListener('popstate', onPopState);
    void api<UserProfile>('/api/store/account').then((profile) => {
      setUser(profile);
      void loadCart();
    }).catch((error) => {
      if (error instanceof ApiError && error.status === 401) setUser(null);
      else setNotice(error instanceof Error ? error.message : '登录状态读取失败');
      setCartLoading(false);
    });
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const protectedRoute = ['checkout', 'orders', 'order', 'account', 'addresses'].includes(route.kind);

  async function logout() {
    try {
      await api<void>('/api/store/auth/logout', { method: 'POST' });
      csrfToken = '';
      setUser(null);
      setCart(emptyCart);
      setCartOpen(false);
      navigate('/mall');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '退出失败');
    }
  }

  async function changeQuantity(item: CartItem, quantity: number) {
    if (quantity < 1 || cartBusy) return;
    setCartBusy(true);
    setNotice('');
    try {
      setCart(await api<CartResponse>(`/api/store/cart/${item.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ quantity })
      }));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '数量修改失败');
    } finally {
      setCartBusy(false);
    }
  }

  async function removeItem(itemId: number) {
    if (cartBusy) return;
    setCartBusy(true);
    setNotice('');
    try {
      setCart(await api<CartResponse>(`/api/store/cart/${itemId}`, { method: 'DELETE' }));
    } catch (error) {
      setNotice(error instanceof Error ? error.message : '删除失败');
    } finally {
      setCartBusy(false);
    }
  }

  return (
    <main className="store-shell">
      <StoreHeader user={user} cartCount={cart.totalQuantity} navigate={navigate}
        onLogout={() => void logout()} onCart={() => user ? setCartOpen(true) : navigate(loginPath('/mall'))} />

      {notice && (
        <div className="store-alert" role="status">
          <span>{notice}</span>
          <button type="button" onClick={() => setNotice('')} title="关闭提示"><X size={16} /></button>
        </div>
      )}

      {user === undefined ? <StoreState icon={<Loader2 className="spin" size={28} />} text="正在读取登录状态" />
        : protectedRoute && !user ? <LoginPage navigate={navigate} onAuthenticated={(profile) => {
            setUser(profile); void loadCart();
          }} />
        : <>
      {route.kind === 'list' && <CatalogPage navigate={navigate} />}
      {route.kind === 'login' && <LoginPage navigate={navigate} onAuthenticated={(profile) => {
        setUser(profile); void loadCart();
      }} />}
      {route.kind === 'detail' && (
        <ProductDetailPage
          productId={route.productId}
          navigate={navigate}
          loginPath={loginPath}
          onCartChanged={(next) => { setCart(next); setCartOpen(true); }}
        />
      )}
      {route.kind === 'checkout' && (
        <CheckoutPage cart={cart} cartLoading={cartLoading} navigate={navigate} onOrdered={setCart} />
      )}
      {route.kind === 'orders' && <OrderCenterPage navigate={navigate} />}
      {route.kind === 'order' && <OrderResultPage orderId={route.orderId} navigate={navigate} />}
      {route.kind === 'account' && user && <AccountPage user={user} onChanged={setUser} navigate={navigate} />}
      {route.kind === 'addresses' && <AddressBookPage navigate={navigate} />}
      </>}

      {cartOpen && (
        <div className="cart-layer" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setCartOpen(false);
        }}>
          <aside className="cart-drawer" role="dialog" aria-modal="true" aria-label="购物车">
            <div className="cart-heading">
              <div><ShoppingCart size={20} /><h2>购物车</h2><span>{cart.totalQuantity} 件</span></div>
              <button type="button" onClick={() => setCartOpen(false)} title="关闭购物车"><X size={20} /></button>
            </div>
            {cartLoading ? (
              <div className="empty-cart"><Loader2 className="spin" size={28} /><span>正在读取购物车</span></div>
            ) : cart.items.length === 0 ? (
              <div className="empty-cart"><ShoppingBag size={34} /><strong>购物车还是空的</strong><span>先去选择喜欢的规格吧</span></div>
            ) : (
              <div className="cart-lines">
                {cart.items.map((item) => (
                  <article className="cart-line" key={item.id}>
                    <img src={item.imageUrl} alt="" />
                    <div>
                      <button className="cart-product-link" type="button" onClick={() => {
                        setCartOpen(false); navigate(`/mall/products/${item.productId}`);
                      }}>{item.productName}</button>
                      <small>{specLabel(item.specValues)}</small>
                      <span>{currency(item.price)}</span>
                      <div className="quantity-control">
                        <button type="button" disabled={cartBusy || item.quantity <= 1} onClick={() => void changeQuantity(item, item.quantity - 1)} title="减少数量"><Minus size={14} /></button>
                        <span>{item.quantity}</span>
                        <button type="button" disabled={cartBusy || item.quantity >= item.stock} onClick={() => void changeQuantity(item, item.quantity + 1)} title="增加数量"><Plus size={14} /></button>
                      </div>
                    </div>
                    <button type="button" disabled={cartBusy} onClick={() => void removeItem(item.id)} title="移除商品"><Trash2 size={17} /></button>
                  </article>
                ))}
              </div>
            )}
            <div className="cart-summary">
              <div><span>商品合计</span><strong>{currency(cart.totalAmount)}</strong></div>
              <button type="button" disabled={!cart.items.length || cartBusy} onClick={() => {
                setCartOpen(false); navigate('/mall/checkout');
              }}>去结算</button>
            </div>
          </aside>
        </div>
      )}
    </main>
  );
}

function StoreHeader({ user, cartCount, navigate, onCart, onLogout }: {
  user: UserProfile | null | undefined;
  cartCount: number;
  navigate: (path: string) => void;
  onCart: () => void;
  onLogout: () => void;
}) {
  return (
    <header className="store-header">
      <a className="store-brand" href="/mall" aria-label="知选商城首页">
        <span className="store-brand-mark"><Store size={22} /></span>
        <span><strong>知选商城</strong><small>好物，刚刚好</small></span>
      </a>
      <form className="store-search" action="/mall" method="get">
        <Search size={18} />
        <input name="keyword" defaultValue={new URLSearchParams(window.location.search).get('keyword') || ''} placeholder="搜索手机、耳机、护肤礼盒" aria-label="搜索商品" />
        <button type="submit">搜索</button>
      </form>
      <div className="store-header-actions">
        {user ? <><button className="account-link" type="button" onClick={() => navigate('/mall/account')}><User size={17} /><span>{user.displayName}</span></button><button className="logout-button" type="button" onClick={onLogout} title="退出登录"><LogOut size={17} /></button></>
          : <button className="account-link" type="button" onClick={() => navigate('/mall/login')}><LogIn size={17} /><span>登录</span></button>}
        <button className="order-center-link" type="button" onClick={() => navigate('/mall/orders')}><ReceiptText size={17} /><span>我的订单</span></button>
        <nav className="mode-tabs" aria-label="功能切换">
          <a href="/"><Bot size={16} /><span>智能助手</span></a>
          <a className="active" href="/mall"><ShoppingBag size={16} /><span>商品商城</span></a>
        </nav>
        <button className="cart-trigger" type="button" onClick={onCart} title="查看购物车">
          <ShoppingCart size={20} />{cartCount > 0 && <span>{cartCount}</span>}
        </button>
      </div>
    </header>
  );
}

function CatalogPage({ navigate }: { navigate: (path: string) => void }) {
  const initialSearch = new URLSearchParams(window.location.search).get('keyword')?.trim() || '';
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [category, setCategory] = useState('');
  const [keyword, setKeyword] = useState(initialSearch);
  const [search, setSearch] = useState(initialSearch);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load(nextCategory = category, nextSearch = search) {
    setLoading(true);
    setError('');
    const params = new URLSearchParams();
    if (nextCategory) params.set('category', nextCategory);
    if (nextSearch) params.set('keyword', nextSearch);
    try {
      const data = await api<ProductListResponse>(`/api/store/products?${params.toString()}`);
      setProducts(data.items);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '商品加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load('', initialSearch);
    void api<Category[]>('/api/store/categories').then(setCategories).catch(() => setCategories([]));
  }, []);

  function selectCategory(next: string) {
    setCategory(next);
    void load(next, search);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const next = keyword.trim();
    setSearch(next);
    void load(category, next);
  }

  return (
    <div className="store-content">
      <aside className="category-rail">
        <h2>商品分类</h2>
        <button className={!category ? 'active' : ''} type="button" onClick={() => selectCategory('')}><span>全部商品</span><strong>{categories.reduce((sum, item) => sum + item.count, 0)}</strong></button>
        {categories.map((item) => <button className={category === item.name ? 'active' : ''} type="button" key={item.name} onClick={() => selectCategory(item.name)}><span>{item.name}</span><strong>{item.count}</strong></button>)}
        <div className="assistant-entry"><Bot size={20} /><strong>不知道怎么选？</strong><span>让智能助手理解需求并推荐商品</span><a href="/">去问问 <ChevronRight size={15} /></a></div>
      </aside>
      <section className="store-main">
        <section className="store-promo">
          <div className="store-promo-copy"><span>本周精选</span><h1>轻装焕新季</h1><p>精选数码、穿搭与生活好物，限时直降。</p><button type="button" onClick={() => selectCategory('数码家电')}>浏览数码好物 <ChevronRight size={17} /></button></div>
          <img src="https://images.unsplash.com/photo-1550009158-9ebf69173e03?auto=format&fit=crop&w=1200&q=85" alt="精选数码商品陈列" />
        </section>
        <form className="catalog-search-mobile" onSubmit={submit}><Search size={17} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索商品" /><button type="submit">搜索</button></form>
        <div className="product-toolbar"><div><h2>{category || (search ? `“${search}”的搜索结果` : '人气好物')}</h2><span>共 {products.length} 件商品</span></div>{(category || search) && <button type="button" onClick={() => { setCategory(''); setKeyword(''); setSearch(''); void load('', ''); }}>清除筛选 <X size={15} /></button>}</div>
        {loading ? <StoreState icon={<Loader2 className="spin" size={28} />} text="正在加载商品" />
          : error ? <StoreState icon={<Package size={30} />} text={error} />
          : products.length === 0 ? <StoreState icon={<Package size={30} />} text="没有找到相关商品" />
          : <div className="product-grid">{products.map((product) => (
            <article className="product-card" key={product.id} onClick={() => navigate(`/mall/products/${product.id}`)}>
              <div className="product-visual"><div className="product-image-fallback"><Package size={32} /><span>{product.category}</span></div><img src={product.imageUrl} alt={product.name} loading="lazy" />{product.featured && <span className="featured-label">精选</span>}</div>
              <div className="product-card-body"><span className="product-category">{product.category}</span><h3>{product.name}</h3><p>{product.subtitle}</p><div className="product-rating"><Star size={14} fill="currentColor" /><strong>{product.rating}</strong><span>已售 {product.sales}</span></div><div className="product-buy-row"><div><strong>{currency(product.price)}</strong><del>{currency(product.originalPrice)}</del></div><button type="button" onClick={(event) => { event.stopPropagation(); navigate(`/mall/products/${product.id}`); }} title="选择规格"><ChevronRight size={19} /></button></div></div>
            </article>
          ))}</div>}
      </section>
    </div>
  );
}

function ProductDetailPage({ productId, navigate, loginPath, onCartChanged }: {
  productId: number;
  navigate: (path: string) => void;
  loginPath: (next?: string) => string;
  onCartChanged: (cart: CartResponse) => void;
}) {
  const [detail, setDetail] = useState<ProductDetail | null>(null);
  const [selectedSkuId, setSelectedSkuId] = useState<number>();
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    void api<ProductDetail>(`/api/store/products/${productId}`).then((data) => {
      setDetail(data);
      setSelectedSkuId(data.skus.find((sku) => sku.stock > 0)?.id);
    }).catch((caught) => setError(caught instanceof Error ? caught.message : '商品详情加载失败')).finally(() => setLoading(false));
  }, [productId]);

  const selectedSku = detail?.skus.find((sku) => sku.id === selectedSkuId);

  async function addToCart() {
    if (!selectedSku || busy) return;
    setBusy(true);
    setError('');
    try {
      const cart = await api<CartResponse>('/api/store/cart', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ skuId: selectedSku.id, quantity })
      });
      onCartChanged(cart);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        navigate(loginPath(`/mall/products/${productId}`));
        return;
      }
      setError(caught instanceof Error ? caught.message : '加入购物车失败');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <StoreState icon={<Loader2 className="spin" size={28} />} text="正在加载商品详情" />;
  if (!detail) return <StoreState icon={<Package size={30} />} text={error || '商品不存在'} />;

  return (
    <section className="detail-page store-page-width">
      <button className="back-link" type="button" onClick={() => navigate('/mall')}><ArrowLeft size={17} />返回商品列表</button>
      <div className="detail-layout">
        <div className="detail-media"><img src={selectedSku?.imageUrl || detail.product.imageUrl} alt={detail.product.name} /></div>
        <div className="detail-info">
          <span className="product-category">{detail.product.category}</span>
          <h1>{detail.product.name}</h1><p className="detail-subtitle">{detail.product.subtitle}</p>
          <div className="detail-rating"><Star size={16} fill="currentColor" /><strong>{detail.product.rating}</strong><span>累计销售 {detail.product.sales}</span></div>
          <div className="detail-price"><strong>{currency(selectedSku?.price ?? detail.product.price)}</strong><del>{currency(selectedSku?.originalPrice ?? detail.product.originalPrice)}</del></div>
          <div className="sku-section"><h2>选择规格</h2><div className="sku-grid">{detail.skus.map((sku) => <button className={selectedSkuId === sku.id ? 'active' : ''} type="button" key={sku.id} disabled={sku.stock < 1} onClick={() => { setSelectedSkuId(sku.id); setQuantity(1); }}><strong>{specLabel(sku.specValues)}</strong><span>{sku.stock > 0 ? `${currency(sku.price)} · 库存 ${sku.stock}` : '暂时无货'}</span>{selectedSkuId === sku.id && <Check size={16} />}</button>)}</div></div>
          <div className="detail-actions"><div className="detail-quantity"><button type="button" disabled={quantity <= 1} onClick={() => setQuantity((value) => value - 1)}><Minus size={16} /></button><span>{quantity}</span><button type="button" disabled={!selectedSku || quantity >= selectedSku.stock} onClick={() => setQuantity((value) => value + 1)}><Plus size={16} /></button></div><button className="add-cart-button" type="button" disabled={!selectedSku || busy} onClick={() => void addToCart()}>{busy ? <Loader2 className="spin" size={18} /> : <ShoppingCart size={18} />}加入购物车</button></div>
          {error && <p className="inline-error">{error}</p>}
          <div className="detail-description"><h2>商品详情</h2><p>{detail.product.description}</p><span>SKU 编码：{selectedSku?.skuCode || '请选择规格'}</span></div>
        </div>
      </div>
    </section>
  );
}

function CheckoutPage({ cart, cartLoading, navigate, onOrdered }: {
  cart: CartResponse;
  cartLoading: boolean;
  navigate: (path: string) => void;
  onOrdered: (cart: CartResponse) => void;
}) {
  const [selected, setSelected] = useState<number[]>([]);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [addressId, setAddressId] = useState<number>();
  const [addressesLoading, setAddressesLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => setSelected(cart.items.map((item) => item.id)), [cart.items]);
  useEffect(() => {
    void api<Address[]>('/api/store/addresses').then((items) => {
      setAddresses(items);
      setAddressId(items.find((item) => item.defaultAddress)?.id || items[0]?.id);
    }).catch((caught) => setError(caught instanceof Error ? caught.message : '地址加载失败'))
      .finally(() => setAddressesLoading(false));
  }, []);
  const selectedItems = cart.items.filter((item) => selected.includes(item.id));
  const total = selectedItems.reduce((sum, item) => sum + item.subtotal, 0);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selected.length || busy) return;
    setBusy(true); setError('');
    try {
      const order = await api<OrderResponse>('/api/store/orders', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cartItemIds: selected, addressId })
      });
      onOrdered({ items: cart.items.filter((item) => !selected.includes(item.id)), totalQuantity: cart.items.filter((item) => !selected.includes(item.id)).reduce((sum, item) => sum + item.quantity, 0), totalAmount: cart.items.filter((item) => !selected.includes(item.id)).reduce((sum, item) => sum + item.subtotal, 0) });
      navigate(`/mall/orders/${order.id}`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '订单创建失败');
    } finally { setBusy(false); }
  }

  if (cartLoading) return <StoreState icon={<Loader2 className="spin" size={28} />} text="正在准备结算" />;
  if (!cart.items.length) return <section className="checkout-empty store-page-width"><ShoppingBag size={38} /><h1>没有可结算的商品</h1><button type="button" onClick={() => navigate('/mall')}>返回商城</button></section>;

  return (
    <form className="checkout-page store-page-width" onSubmit={submit}>
      <button className="back-link" type="button" onClick={() => navigate('/mall')}><ArrowLeft size={17} />继续购物</button>
      <div className="checkout-heading"><div><span>确认订单</span><h1>核对商品与收货信息</h1></div><strong>{selectedItems.length} 种商品</strong></div>
      <div className="checkout-layout">
        <div className="checkout-main">
          <section className="checkout-section"><div className="section-title-row"><h2>收货地址</h2><button className="address-manage-link" type="button" onClick={() => navigate('/mall/account/addresses')}><MapPin size={15} />管理地址</button></div>{addressesLoading ? <div className="address-loading"><Loader2 className="spin" size={20} />正在读取地址</div> : addresses.length === 0 ? <div className="address-empty-inline"><MapPin size={24} /><span>还没有收货地址</span><button type="button" onClick={() => navigate('/mall/account/addresses')}>新增地址</button></div> : <div className="checkout-addresses">{addresses.map((address) => <label className={addressId === address.id ? 'selected' : ''} key={address.id}><input type="radio" name="address" checked={addressId === address.id} onChange={() => setAddressId(address.id)} /><span><strong>{address.receiverName} · {address.receiverPhone}</strong><small>{address.fullAddress}</small></span>{address.defaultAddress && <em>默认</em>}</label>)}</div>}</section>
          <section className="checkout-section"><div className="section-title-row"><h2>商品清单</h2><label><input type="checkbox" checked={selected.length === cart.items.length} onChange={(event) => setSelected(event.target.checked ? cart.items.map((item) => item.id) : [])} />全选</label></div><div className="checkout-items">{cart.items.map((item) => <label className="checkout-item" key={item.id}><input type="checkbox" checked={selected.includes(item.id)} onChange={(event) => setSelected((current) => event.target.checked ? [...current, item.id] : current.filter((id) => id !== item.id))} /><img src={item.imageUrl} alt="" /><span><strong>{item.productName}</strong><small>{specLabel(item.specValues)}</small></span><span>{currency(item.price)} × {item.quantity}</span><strong>{currency(item.subtotal)}</strong></label>)}</div></section>
        </div>
        <aside className="order-summary"><h2>订单汇总</h2><div><span>商品件数</span><strong>{selectedItems.reduce((sum, item) => sum + item.quantity, 0)} 件</strong></div><div><span>运费</span><strong>免运费</strong></div><div className="order-total"><span>应付金额</span><strong>{currency(total)}</strong></div>{error && <p className="inline-error">{error}</p>}<button type="submit" disabled={!selected.length || !addressId || busy}>{busy ? <Loader2 className="spin" size={18} /> : <Check size={18} />}提交模拟订单</button><small>本次下单不会发起真实支付</small></aside>
      </div>
    </form>
  );
}

function LoginPage({ navigate, onAuthenticated }: {
  navigate: (path: string) => void;
  onAuthenticated: (user: UserProfile) => void;
}) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      const path = mode === 'login' ? '/api/store/auth/login' : '/api/store/auth/register';
      const profile = await api<UserProfile>(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(mode === 'login' ? { username, password } : { username, password, displayName })
      });
      onAuthenticated(profile);
      const queryNext = new URLSearchParams(window.location.search).get('next');
      const next = window.location.pathname !== '/mall/login' ? window.location.pathname
        : queryNext?.startsWith('/mall') && !queryNext.startsWith('/mall/login') ? queryNext : '/mall';
      navigate(next);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '认证失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="auth-page store-page-width">
      <div className="auth-layout">
        <div className="auth-context"><span>知选会员</span><h1>登录后继续你的购物旅程</h1><p>购物车、订单和收货地址只属于当前账号，并通过服务端会话保持登录。</p><div><ShoppingCart size={19} /><span>跨页面保留购物车</span></div><div><ReceiptText size={19} /><span>随时查看订单状态</span></div><div><MapPin size={19} /><span>管理常用收货地址</span></div></div>
        <form className="auth-form" onSubmit={submit}>
          <div className="auth-tabs" role="tablist"><button className={mode === 'login' ? 'active' : ''} type="button" onClick={() => { setMode('login'); setError(''); }}>登录</button><button className={mode === 'register' ? 'active' : ''} type="button" onClick={() => { setMode('register'); setError(''); }}>注册</button></div>
          <header><span>{mode === 'login' ? '欢迎回来' : '创建商城账号'}</span><h2>{mode === 'login' ? '登录知选商城' : '注册新用户'}</h2></header>
          {mode === 'register' && <label><span>昵称</span><input required maxLength={40} value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="你的称呼" autoComplete="name" /></label>}
          <label><span>用户名</span><input required minLength={3} maxLength={30} pattern="[A-Za-z0-9_]+" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="3-30 位字母、数字或下划线" autoComplete="username" /></label>
          <label><span>密码</span><input required minLength={8} maxLength={72} type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="至少 8 位" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></label>
          {error && <p className="inline-error">{error}</p>}
          <button className="auth-submit" type="submit" disabled={busy}>{busy ? <Loader2 className="spin" size={18} /> : mode === 'login' ? <LogIn size={18} /> : <User size={18} />}{mode === 'login' ? '登录' : '注册并登录'}</button>
          <button className="auth-back" type="button" onClick={() => navigate('/mall')}><ArrowLeft size={15} />返回商城</button>
        </form>
      </div>
    </section>
  );
}

function AccountPage({ user, onChanged, navigate }: {
  user: UserProfile;
  onChanged: (user: UserProfile) => void;
  navigate: (path: string) => void;
}) {
  const [displayName, setDisplayName] = useState(user.displayName);
  const [phone, setPhone] = useState(user.phone || '');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setNotice('');
    try {
      const updated = await api<UserProfile>('/api/store/account', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName, phone })
      });
      onChanged(updated); setNotice('个人资料已保存');
    } catch (caught) { setNotice(caught instanceof Error ? caught.message : '保存失败'); }
    finally { setBusy(false); }
  }

  return (
    <section className="account-page store-page-width">
      <div className="account-heading"><div><span>个人中心</span><h1>{user.displayName}</h1><p>@{user.username} · 注册于 {new Date(user.createdAt).toLocaleDateString('zh-CN')}</p></div><div className="account-avatar"><User size={30} /></div></div>
      <div className="account-layout">
        <nav className="account-nav"><button className="active" type="button"><User size={17} />个人资料</button><button type="button" onClick={() => navigate('/mall/account/addresses')}><MapPin size={17} />收货地址<ChevronRight size={15} /></button><button type="button" onClick={() => navigate('/mall/orders')}><ReceiptText size={17} />我的订单<ChevronRight size={15} /></button></nav>
        <form className="profile-form" onSubmit={submit}><header><h2>基本资料</h2><span>用于商城内的账号展示</span></header><label><span>用户名</span><input value={user.username} disabled /></label><label><span>昵称</span><input required maxLength={40} value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label><label><span>手机号</span><input maxLength={30} value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="可选" /></label>{notice && <p className="profile-notice">{notice}</p>}<button type="submit" disabled={busy}>{busy ? <Loader2 className="spin" size={17} /> : <Check size={17} />}保存资料</button></form>
      </div>
    </section>
  );
}

const emptyAddress: AddressDraft = {
  receiverName: '', receiverPhone: '', province: '', city: '', district: '',
  detailAddress: '', postalCode: '', defaultAddress: false
};

function AddressBookPage({ navigate }: { navigate: (path: string) => void }) {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [draft, setDraft] = useState<AddressDraft>(emptyAddress);
  const [editingId, setEditingId] = useState<number>();
  const [formOpen, setFormOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true); setError('');
    try { setAddresses(await api<Address[]>('/api/store/addresses')); }
    catch (caught) { setError(caught instanceof Error ? caught.message : '地址加载失败'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  function openCreate() { setEditingId(undefined); setDraft(emptyAddress); setFormOpen(true); setError(''); }
  function openEdit(address: Address) {
    setEditingId(address.id);
    setDraft({ receiverName: address.receiverName, receiverPhone: address.receiverPhone,
      province: address.province || '', city: address.city || '', district: address.district || '',
      detailAddress: address.detailAddress, postalCode: address.postalCode || '',
      defaultAddress: address.defaultAddress });
    setFormOpen(true); setError('');
  }
  function change<K extends keyof AddressDraft>(key: K, value: AddressDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const saved = await api<Address>(editingId ? `/api/store/addresses/${editingId}` : '/api/store/addresses', {
        method: editingId ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(draft)
      });
      setFormOpen(false); setEditingId(undefined);
      setAddresses((current) => {
        const next = editingId ? current.map((item) => item.id === saved.id ? saved : item) : [saved, ...current];
        return saved.defaultAddress ? next.map((item) => item.id === saved.id ? saved : { ...item, defaultAddress: false }) : next;
      });
    } catch (caught) { setError(caught instanceof Error ? caught.message : '地址保存失败'); }
    finally { setBusy(false); }
  }

  async function remove(id: number) {
    if (busy || !window.confirm('确认删除这个收货地址吗？')) return;
    setBusy(true); setError('');
    try { setAddresses(await api<Address[]>(`/api/store/addresses/${id}`, { method: 'DELETE' })); }
    catch (caught) { setError(caught instanceof Error ? caught.message : '地址删除失败'); }
    finally { setBusy(false); }
  }

  async function makeDefault(id: number) {
    if (busy) return; setBusy(true); setError('');
    try {
      const updated = await api<Address>(`/api/store/addresses/${id}/default`, { method: 'PUT' });
      setAddresses((current) => current.map((item) => ({ ...item, defaultAddress: item.id === updated.id })));
    } catch (caught) { setError(caught instanceof Error ? caught.message : '默认地址设置失败'); }
    finally { setBusy(false); }
  }

  return (
    <section className="address-page store-page-width">
      <div className="address-heading"><div><button className="back-link" type="button" onClick={() => navigate('/mall/account')}><ArrowLeft size={16} />个人中心</button><span>地址管理</span><h1>收货地址</h1><p>结算时可以直接选择这里保存的地址。</p></div><button type="button" onClick={openCreate}><Plus size={17} />新增地址</button></div>
      {error && !formOpen && <div className="order-center-error">{error}<button type="button" onClick={() => void load()}>重试</button></div>}
      {loading ? <StoreState icon={<Loader2 className="spin" size={28} />} text="正在加载地址" /> : addresses.length === 0 ? <div className="address-book-empty"><MapPin size={36} /><strong>还没有收货地址</strong><span>新增一个地址后就能用于结算</span><button type="button" onClick={openCreate}>新增地址</button></div> : <div className="address-list">{addresses.map((address) => <article className={address.defaultAddress ? 'default' : ''} key={address.id}><header><Home size={18} /><strong>{address.receiverName}</strong><span>{address.receiverPhone}</span>{address.defaultAddress && <em>默认地址</em>}</header><p>{address.fullAddress}</p>{address.postalCode && <small>邮编 {address.postalCode}</small>}<footer>{!address.defaultAddress && <button type="button" disabled={busy} onClick={() => void makeDefault(address.id)}>设为默认</button>}<button type="button" onClick={() => openEdit(address)}><Pencil size={14} />编辑</button><button type="button" disabled={busy} onClick={() => void remove(address.id)}><Trash2 size={14} />删除</button></footer></article>)}</div>}
      {formOpen && <div className="address-modal" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) setFormOpen(false); }}><form className="address-form" onSubmit={save}><header><div><span>{editingId ? '编辑地址' : '新增地址'}</span><h2>收货信息</h2></div><button type="button" disabled={busy} onClick={() => setFormOpen(false)} title="关闭"><X size={20} /></button></header><div className="address-form-grid"><label><span>收货人</span><input required maxLength={40} value={draft.receiverName} onChange={(event) => change('receiverName', event.target.value)} /></label><label><span>联系电话</span><input required maxLength={30} value={draft.receiverPhone} onChange={(event) => change('receiverPhone', event.target.value)} /></label><label><span>省/直辖市</span><input maxLength={40} value={draft.province || ''} onChange={(event) => change('province', event.target.value)} /></label><label><span>城市</span><input maxLength={40} value={draft.city || ''} onChange={(event) => change('city', event.target.value)} /></label><label><span>区县</span><input maxLength={40} value={draft.district || ''} onChange={(event) => change('district', event.target.value)} /></label><label><span>邮政编码</span><input maxLength={20} value={draft.postalCode || ''} onChange={(event) => change('postalCode', event.target.value)} /></label><label className="address-form-wide"><span>详细地址</span><input required maxLength={240} value={draft.detailAddress} onChange={(event) => change('detailAddress', event.target.value)} placeholder="街道、门牌号、小区、楼栋" /></label></div><label className="default-toggle"><input type="checkbox" checked={draft.defaultAddress} onChange={(event) => change('defaultAddress', event.target.checked)} /><span>设为默认收货地址</span></label>{error && <p className="inline-error">{error}</p>}<footer><button type="button" disabled={busy} onClick={() => setFormOpen(false)}>取消</button><button type="submit" disabled={busy}>{busy ? <Loader2 className="spin" size={17} /> : <Check size={17} />}保存地址</button></footer></form></div>}
    </section>
  );
}

type OrderStatusFilter = 'ALL' | 'CREATED' | 'PAID' | 'CANCELLED';

const orderStatusOptions: Array<{ value: OrderStatusFilter; label: string }> = [
  { value: 'ALL', label: '全部订单' },
  { value: 'CREATED', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'CANCELLED', label: '已取消' }
];

function orderStatusLabel(status: string) {
  if (status === 'PAID') return '已支付';
  if (status === 'CANCELLED') return '已取消';
  return '待支付';
}

function OrderCenterPage({ navigate }: { navigate: (path: string) => void }) {
  const [status, setStatus] = useState<OrderStatusFilter>('ALL');
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number>();
  const [error, setError] = useState('');

  async function loadOrders(nextStatus: OrderStatusFilter) {
    setLoading(true);
    setError('');
    try {
      const query = nextStatus === 'ALL' ? '' : `?status=${nextStatus}`;
      setOrders(await api<OrderResponse[]>(`/api/store/orders${query}`));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '订单加载失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadOrders(status); }, [status]);

  async function operate(order: OrderResponse, action: 'pay' | 'cancel') {
    if (busyId) return;
    if (action === 'cancel' && !window.confirm('确认取消这个订单并恢复商品库存吗？')) return;
    setBusyId(order.id);
    setError('');
    try {
      const updated = await api<OrderResponse>(`/api/store/orders/${order.id}/${action}`, { method: 'POST' });
      if (status !== 'ALL' && updated.status !== status) {
        setOrders((current) => current.filter((item) => item.id !== updated.id));
      } else {
        setOrders((current) => current.map((item) => item.id === updated.id ? updated : item));
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '订单操作失败');
    } finally {
      setBusyId(undefined);
    }
  }

  return (
    <section className="order-center store-page-width">
      <div className="order-center-heading">
        <div><span>订单中心</span><h1>我的订单</h1><p>查看订单状态，完成模拟支付或取消待支付订单。</p></div>
        <button type="button" onClick={() => navigate('/mall')}><ShoppingBag size={17} />继续购物</button>
      </div>
      <div className="order-filter-tabs" role="tablist" aria-label="订单状态筛选">
        {orderStatusOptions.map((option) => (
          <button className={status === option.value ? 'active' : ''} type="button" role="tab" aria-selected={status === option.value} key={option.value} onClick={() => setStatus(option.value)}>{option.label}</button>
        ))}
      </div>
      {error && <div className="order-center-error">{error}<button type="button" onClick={() => void loadOrders(status)}>重试</button></div>}
      {loading ? <StoreState icon={<Loader2 className="spin" size={28} />} text="正在加载订单" />
        : orders.length === 0 ? <div className="orders-empty"><ReceiptText size={36} /><strong>这里还没有相关订单</strong><span>去商城挑选喜欢的商品吧</span><button type="button" onClick={() => navigate('/mall')}>浏览商品</button></div>
        : <div className="order-list">{orders.map((order) => (
          <article className="order-card" key={order.id}>
            <header><div><span>{new Date(order.createdAt).toLocaleString('zh-CN')}</span><strong>{order.orderNo}</strong></div><span className={`order-status ${order.status.toLowerCase()}`}>{orderStatusLabel(order.status)}</span></header>
            <div className="order-card-body">
              <div className="order-product-stack">{order.items.slice(0, 3).map((item) => <img src={item.imageUrl} alt={item.productName} key={`${order.id}-${item.skuId}`} />)}<div><strong>{order.items[0]?.productName}</strong><span>{order.items.length > 1 ? `等 ${order.items.length} 种商品` : specLabel(order.items[0]?.specValues || {})}</span></div></div>
              <div className="order-card-total"><span>订单金额</span><strong>{currency(order.totalAmount)}</strong></div>
            </div>
            <footer>
              <button className="order-detail-button" type="button" onClick={() => navigate(`/mall/orders/${order.id}`)}>查看详情</button>
              {order.status === 'CREATED' && <><button className="order-cancel-button" type="button" disabled={busyId === order.id} onClick={() => void operate(order, 'cancel')}><RotateCcw size={15} />取消订单</button><button className="order-pay-button" type="button" disabled={busyId === order.id} onClick={() => void operate(order, 'pay')}>{busyId === order.id ? <Loader2 className="spin" size={15} /> : <CreditCard size={15} />}模拟支付</button></>}
            </footer>
          </article>
        ))}</div>}
    </section>
  );
}

function OrderResultPage({ orderId, navigate }: { orderId: number; navigate: (path: string) => void }) {
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { void api<OrderResponse>(`/api/store/orders/${orderId}`).then(setOrder).catch((caught) => setError(caught instanceof Error ? caught.message : '订单加载失败')); }, [orderId]);

  async function operate(action: 'pay' | 'cancel') {
    if (!order || busy) return;
    if (action === 'cancel' && !window.confirm('确认取消这个订单并恢复商品库存吗？')) return;
    setBusy(true); setError('');
    try {
      setOrder(await api<OrderResponse>(`/api/store/orders/${order.id}/${action}`, { method: 'POST' }));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '订单操作失败');
    } finally { setBusy(false); }
  }

  if (!order && !error) return <StoreState icon={<Loader2 className="spin" size={28} />} text="正在读取订单" />;
  if (!order) return <StoreState icon={<Package size={30} />} text={error} />;
  const isPaid = order.status === 'PAID';
  const isCancelled = order.status === 'CANCELLED';
  return (
    <section className="order-result store-page-width">
      <div className={`order-success ${order.status.toLowerCase()}`}>
        {isPaid ? <CircleCheck size={42} /> : isCancelled ? <RotateCcw size={42} /> : <Clock3 size={42} />}
        <span>{orderStatusLabel(order.status)}</span>
        <h1>{isPaid ? '模拟支付成功' : isCancelled ? '订单已取消' : '订单已经创建'}</h1>
        <p>{isPaid ? '支付流水已生成，本次不会产生真实扣款。' : isCancelled ? '订单商品库存已经恢复。' : '库存已锁定，请完成模拟支付或取消订单。'}</p>
      </div>
      <div className="order-meta"><div><span>订单编号</span><strong>{order.orderNo}</strong></div><div><span>订单状态</span><strong>{orderStatusLabel(order.status)}</strong></div><div><span>{isPaid ? '支付时间' : isCancelled ? '取消时间' : '下单时间'}</span><strong>{new Date(order.paidAt || order.cancelledAt || order.createdAt).toLocaleString('zh-CN')}</strong></div><div><span>订单金额</span><strong>{currency(order.totalAmount)}</strong></div></div>
      {order.paymentNo && <div className="payment-reference"><span>模拟支付流水</span><code>{order.paymentNo}</code></div>}
      <div className="order-result-layout"><section><h2>商品明细</h2>{order.items.map((item) => <article className="result-item" key={`${item.skuId}-${item.id}`}><img src={item.imageUrl} alt="" /><span><strong>{item.productName}</strong><small>{specLabel(item.specValues)}</small></span><span>{currency(item.price)} × {item.quantity}</span><strong>{currency(item.subtotal)}</strong></article>)}</section><aside><h2>收货信息</h2><strong>{order.receiverName} · {order.receiverPhone}</strong><p>{order.receiverAddress}</p></aside></div>
      {error && <p className="inline-error">{error}</p>}
      <div className="order-result-actions"><button className="order-detail-button" type="button" onClick={() => navigate('/mall/orders')}><ReceiptText size={16} />查看全部订单</button>{order.status === 'CREATED' && <><button className="order-cancel-button" type="button" disabled={busy} onClick={() => void operate('cancel')}><RotateCcw size={16} />取消订单</button><button className="order-pay-button" type="button" disabled={busy} onClick={() => void operate('pay')}>{busy ? <Loader2 className="spin" size={16} /> : <CreditCard size={16} />}模拟支付</button></>}<button className="continue-button" type="button" onClick={() => navigate('/mall')}>继续购物 <ChevronRight size={17} /></button></div>
    </section>
  );
}

function StoreState({ icon, text }: { icon: React.ReactNode; text: string }) {
  return <div className="store-state">{icon}<strong>{text}</strong></div>;
}
