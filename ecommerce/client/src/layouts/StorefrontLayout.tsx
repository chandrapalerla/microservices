import { useState, useRef, useEffect } from 'react'
import { Link, Outlet, useNavigate, NavLink } from 'react-router-dom'
import {
  ShoppingCart, Search, ChevronDown, LogOut, User, Package,
  ShieldCheck, Menu, X, Sun, Moon, MapPin,
} from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/context/AuthContext'
import { useCart } from '@/context/CartContext'
import { useTheme } from '@/context/ThemeContext'
import { getCategories } from '@/api/productApi'
import { APP_NAME, ROLES } from '@/constants'

export function StorefrontLayout() {
  const { user, logout, hasRole } = useAuth()
  const { count } = useCart()
  const { isDark, toggleTheme } = useTheme()
  const navigate = useNavigate()

  const [searchQuery, setSearchQuery] = useState('')
  const [accountOpen, setAccountOpen] = useState(false)
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [mobileCatOpen, setMobileCatOpen] = useState(false)
  const accountRef = useRef<HTMLDivElement>(null)

  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn:  getCategories,
    staleTime: 300_000,
  })

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (accountRef.current && !accountRef.current.contains(e.target as Node)) {
        setAccountOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    if (searchQuery.trim()) {
      navigate(`/shop?name=${encodeURIComponent(searchQuery.trim())}`)
      setSearchQuery('')
    }
  }

  async function handleLogout() {
    setAccountOpen(false)
    await logout()
    navigate('/login', { replace: true })
  }

  const isAdmin = hasRole(ROLES.ADMIN)

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-950">
      {/* ── Main Header ────────────────────────────────────────────────────────── */}
      <header className="bg-[#131921] text-white sticky top-0 z-40">
        <div className="max-w-[1500px] mx-auto px-3 py-2 flex items-center gap-3">

          {/* Mobile menu toggle */}
          <button
            className="lg:hidden p-1.5 hover:bg-[#232f3e] rounded"
            onClick={() => setMobileMenuOpen((o) => !o)}
          >
            {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>

          {/* Logo */}
          <Link
            to="/"
            className="flex items-center gap-1.5 shrink-0 px-2 py-1 rounded hover:ring-1 hover:ring-white/30"
          >
            <div className="bg-[#FF9900] rounded p-1">
              <Package size={18} className="text-[#131921]" />
            </div>
            <span className="font-bold text-lg tracking-tight hidden sm:block">
              {APP_NAME}
            </span>
          </Link>

          {/* Delivery location (desktop) */}
          <div className="hidden lg:flex items-center gap-1 text-xs hover:ring-1 hover:ring-white/30 rounded px-2 py-1 cursor-pointer shrink-0">
            <MapPin size={14} className="text-gray-300" />
            <div>
              <p className="text-gray-300 leading-none">Deliver to</p>
              <p className="font-bold leading-none mt-0.5">India</p>
            </div>
          </div>

          {/* Search bar */}
          <form onSubmit={handleSearch} className="flex-1 flex min-w-0">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search products…"
              className="flex-1 min-w-0 rounded-l-md px-4 py-2 text-gray-900 text-sm focus:outline-none"
            />
            <button
              type="submit"
              className="bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] px-4 rounded-r-md transition-colors"
            >
              <Search size={18} />
            </button>
          </form>

          {/* Right controls */}
          <div className="flex items-center gap-1 shrink-0">
            {/* Theme toggle */}
            <button
              onClick={toggleTheme}
              className="p-2 hover:bg-[#232f3e] rounded hidden sm:block"
              title={isDark ? 'Light mode' : 'Dark mode'}
            >
              {isDark ? <Sun size={16} /> : <Moon size={16} />}
            </button>

            {/* Account */}
            <div ref={accountRef} className="relative">
              <button
                onClick={() => setAccountOpen((o) => !o)}
                className="flex flex-col items-start px-2 py-1 hover:ring-1 hover:ring-white/30 rounded text-xs"
              >
                <span className="text-gray-300 leading-none">
                  {user ? `Hello, ${user.displayName.split(' ')[0]}` : 'Hello, Sign in'}
                </span>
                <span className="font-bold leading-none mt-0.5 flex items-center gap-0.5">
                  Account <ChevronDown size={12} />
                </span>
              </button>

              {accountOpen && (
                <div className="absolute right-0 top-full mt-1 w-56 rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-2xl py-1 text-gray-800 dark:text-gray-200 z-50">
                  {user ? (
                    <>
                      <div className="px-4 py-3 border-b border-gray-100 dark:border-gray-700">
                        <p className="font-semibold text-sm truncate">{user.displayName}</p>
                        <p className="text-xs text-gray-500 truncate">{user.email}</p>
                      </div>
                      <Link
                        to="/profile"
                        onClick={() => setAccountOpen(false)}
                        className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                      >
                        <User size={14} /> My Profile
                      </Link>
                      <Link
                        to="/orders"
                        onClick={() => setAccountOpen(false)}
                        className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                      >
                        <Package size={14} /> My Orders
                      </Link>
                      {isAdmin && (
                        <Link
                          to="/admin/dashboard"
                          onClick={() => setAccountOpen(false)}
                          className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 text-purple-600 dark:text-purple-400"
                        >
                          <ShieldCheck size={14} /> Admin Panel
                        </Link>
                      )}
                      <hr className="my-1 border-gray-100 dark:border-gray-700" />
                      <button
                        onClick={handleLogout}
                        className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
                      >
                        <LogOut size={14} /> Sign Out
                      </button>
                    </>
                  ) : (
                    <>
                      <div className="px-4 py-3">
                        <Link
                          to="/login"
                          onClick={() => setAccountOpen(false)}
                          className="block w-full text-center bg-[#FF9900] hover:bg-[#F3A847] text-[#131921] font-semibold py-1.5 rounded text-sm"
                        >
                          Sign In
                        </Link>
                      </div>
                      <hr className="border-gray-100 dark:border-gray-700" />
                      <Link
                        to="/orders"
                        onClick={() => setAccountOpen(false)}
                        className="flex items-center gap-2 px-4 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-700"
                      >
                        <Package size={14} /> My Orders
                      </Link>
                    </>
                  )}
                </div>
              )}
            </div>

            {/* Orders shortcut (desktop) */}
            <Link
              to="/orders"
              className="hidden lg:flex flex-col items-start px-2 py-1 hover:ring-1 hover:ring-white/30 rounded text-xs"
            >
              <span className="text-gray-300 leading-none">Returns</span>
              <span className="font-bold leading-none mt-0.5">& Orders</span>
            </Link>

            {/* Cart */}
            <Link
              to="/cart"
              className="relative flex items-end gap-1 px-2 py-1 hover:ring-1 hover:ring-white/30 rounded"
            >
              <div className="relative">
                <ShoppingCart size={28} />
                {count > 0 && (
                  <span className="absolute -top-1.5 -right-1 bg-[#FF9900] text-[#131921] text-xs font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-0.5">
                    {count > 99 ? '99+' : count}
                  </span>
                )}
              </div>
              <span className="font-bold text-xs hidden sm:block pb-0.5">Cart</span>
            </Link>
          </div>
        </div>

        {/* ── Nav bar ────────────────────────────────────────────────────────── */}
        <nav className="bg-[#232f3e] hidden lg:block">
          <div className="max-w-[1500px] mx-auto px-3 flex items-center gap-1">
            <NavLink
              to="/shop"
              className={({ isActive }) =>
                `px-3 py-2 text-sm text-white/90 hover:bg-[#37475a] whitespace-nowrap rounded ${isActive ? 'bg-[#37475a]' : ''}`
              }
            >
              All Products
            </NavLink>
            {isAdmin && (
              <NavLink
                to="/admin/dashboard"
                className={({ isActive }) =>
                  `px-3 py-2 text-sm whitespace-nowrap rounded flex items-center gap-1.5 ${isActive ? 'bg-purple-700 text-white' : 'text-purple-300 hover:bg-[#37475a] hover:text-purple-200'}`
                }
              >
                <ShieldCheck size={14} /> Admin Panel
              </NavLink>
            )}
          </div>
        </nav>

        {/* ── Mobile menu ────────────────────────────────────────────────────── */}
        {mobileMenuOpen && (
          <div className="lg:hidden bg-[#232f3e] border-t border-white/10 px-4 py-3 space-y-2">
            <form onSubmit={handleSearch} className="flex mb-3">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search products…"
                className="flex-1 rounded-l-md px-3 py-1.5 text-gray-900 text-sm focus:outline-none"
              />
              <button type="submit" className="bg-[#FF9900] px-3 rounded-r-md text-[#131921]">
                <Search size={16} />
              </button>
            </form>
            <button
              onClick={() => setMobileCatOpen((o) => !o)}
              className="flex items-center justify-between w-full text-sm text-white py-1.5"
            >
              Browse Categories <ChevronDown size={14} className={mobileCatOpen ? 'rotate-180' : ''} />
            </button>
            {mobileCatOpen && (
              <div className="pl-3 space-y-1">
                <Link
                  to="/shop"
                  onClick={() => setMobileMenuOpen(false)}
                  className="block text-sm text-white/80 py-1 hover:text-white"
                >
                  All Products
                </Link>
                {categories.map((cat) => (
                  <Link
                    key={cat.id}
                    to={`/shop?categoryId=${cat.id}`}
                    onClick={() => setMobileMenuOpen(false)}
                    className="block text-sm text-white/80 py-1 hover:text-white"
                  >
                    {cat.name}
                  </Link>
                ))}
              </div>
            )}
            <hr className="border-white/10" />
            {user ? (
              <>
                <Link to="/orders" onClick={() => setMobileMenuOpen(false)} className="block text-sm text-white/90 py-1.5">My Orders</Link>
                <Link to="/profile" onClick={() => setMobileMenuOpen(false)} className="block text-sm text-white/90 py-1.5">My Profile</Link>
                {isAdmin && <Link to="/admin/dashboard" onClick={() => setMobileMenuOpen(false)} className="block text-sm text-purple-300 py-1.5">Admin Panel</Link>}
                <button onClick={handleLogout} className="block text-sm text-red-400 py-1.5 w-full text-left">Sign Out</button>
              </>
            ) : (
              <Link to="/login" onClick={() => setMobileMenuOpen(false)} className="block text-sm font-semibold text-[#FF9900] py-1.5">Sign In</Link>
            )}
          </div>
        )}
      </header>

      {/* ── Page Content ───────────────────────────────────────────────────────── */}
      <main className="flex-1">
        <Outlet />
      </main>

      {/* ── Footer ─────────────────────────────────────────────────────────────── */}
      <footer className="bg-[#131921] text-gray-400 mt-8">
        <div className="bg-[#232f3e] py-10">
          <div className="max-w-[1200px] mx-auto px-6 grid grid-cols-2 md:grid-cols-4 gap-8">
            <div>
              <h4 className="text-white font-semibold mb-3 text-sm">Get to Know Us</h4>
              <ul className="space-y-2 text-sm">
                <li><a href="#" className="hover:text-white">About Us</a></li>
                <li><a href="#" className="hover:text-white">Careers</a></li>
                <li><a href="#" className="hover:text-white">Press Releases</a></li>
              </ul>
            </div>
            <div>
              <h4 className="text-white font-semibold mb-3 text-sm">Connect with Us</h4>
              <ul className="space-y-2 text-sm">
                <li><a href="#" className="hover:text-white">Facebook</a></li>
                <li><a href="#" className="hover:text-white">Twitter</a></li>
                <li><a href="#" className="hover:text-white">Instagram</a></li>
              </ul>
            </div>
            <div>
              <h4 className="text-white font-semibold mb-3 text-sm">Make Money with Us</h4>
              <ul className="space-y-2 text-sm">
                <li><a href="#" className="hover:text-white">Sell on {APP_NAME}</a></li>
                <li><a href="#" className="hover:text-white">Become an Affiliate</a></li>
              </ul>
            </div>
            <div>
              <h4 className="text-white font-semibold mb-3 text-sm">Let Us Help You</h4>
              <ul className="space-y-2 text-sm">
                <li><Link to="/orders" className="hover:text-white">Your Orders</Link></li>
                <li><Link to="/profile" className="hover:text-white">Your Account</Link></li>
                <li><a href="#" className="hover:text-white">Help</a></li>
              </ul>
            </div>
          </div>
        </div>
        <div className="py-4 text-center text-xs text-gray-500">
          © {new Date().getFullYear()} {APP_NAME}. All rights reserved.
        </div>
      </footer>
    </div>
  )
}
