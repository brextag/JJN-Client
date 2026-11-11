import { Home, Users, Sparkles, Puzzle, Settings, UserCircle, Gauge } from 'lucide-react'
import { clsx } from 'clsx'
import type { PageKey } from '../App'

const items: { id: PageKey; label: string; icon: React.ElementType }[] = [
  { id: 'home', label: 'Home', icon: Home },
  { id: 'multiplayer', label: 'Multiplayer', icon: Users },
  { id: 'cosmetics', label: 'Cosmetics', icon: Sparkles },
  { id: 'mods', label: 'Mods', icon: Puzzle },
  { id: 'settings', label: 'Settings', icon: Settings },
  { id: 'accounts', label: 'Accounts', icon: UserCircle },
  { id: 'performance', label: 'Performance', icon: Gauge },
]

interface Props {
  active: PageKey
  onChange: (page: PageKey) => void
}

export default function Sidebar({ active, onChange }: Props) {
  return (
    <aside className="w-20 lg:w-64 h-full glass-panel m-3 flex flex-col py-6 px-3 gap-1">
      {/* Logo */}
      <div className="flex items-center gap-3 px-3 mb-8">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-jjn-500 to-jjn-700 flex items-center justify-center font-bold text-lg shadow-lg shadow-jjn-500/30">
          JJ
        </div>
        <div className="hidden lg:block">
          <div className="font-semibold text-sm tracking-wide">JJN Client</div>
          <div className="text-[11px] text-white/40">Premium</div>
        </div>
      </div>

      <nav className="flex-1 flex flex-col gap-1">
        {items.map(({ id, label, icon: Icon }) => {
          const isActive = active === id
          return (
            <button
              key={id}
              onClick={() => onChange(id)}
              className={clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group',
                isActive
                  ? 'bg-white/10 text-white'
                  : 'text-white/50 hover:text-white/80 hover:bg-white/5'
              )}
            >
              <Icon
                size={20}
                className={clsx(
                  'transition-colors',
                  isActive ? 'text-jjn-400' : 'text-white/40 group-hover:text-white/70'
                )}
              />
              <span className="hidden lg:block text-sm font-medium">{label}</span>
              {isActive && (
                <div className="ml-auto w-1.5 h-1.5 rounded-full bg-jjn-400 hidden lg:block" />
              )}
            </button>
          )
        })}
      </nav>

      <div className="mt-auto px-3 pt-4 border-t border-white/5">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-jjn-400 to-purple-600 flex items-center justify-center text-xs font-semibold">
            JS
          </div>
          <div className="hidden lg:block overflow-hidden">
            <div className="text-sm font-medium truncate">Player</div>
            <div className="text-[11px] text-white/40">Microsoft Account</div>
          </div>
        </div>
      </div>
    </aside>
  )
}
