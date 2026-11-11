import { Plus, LogOut } from 'lucide-react'

export default function Accounts() {
  return (
    <div className="h-full p-6 overflow-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Accounts</h1>
          <p className="text-white/40 text-sm mt-1">Manage your Minecraft accounts</p>
        </div>
        <button className="glass-button px-4 py-2 rounded-xl text-sm flex items-center gap-2">
          <Plus size={16} />
          Add Account
        </button>
      </div>

      <div className="space-y-3 max-w-2xl">
        <div className="glass-panel p-5 flex items-center gap-4 ring-1 ring-jjn-500/30">
          <div className="w-14 h-14 rounded-full bg-gradient-to-br from-jjn-400 to-purple-600 flex items-center justify-center text-lg font-semibold">
            JS
          </div>
          <div className="flex-1">
            <div className="font-medium">Player</div>
            <div className="text-sm text-white/40">Microsoft Account • Active</div>
          </div>
          <button className="text-white/40 hover:text-white transition-colors p-2">
            <LogOut size={18} />
          </button>
        </div>

        <div className="glass-panel p-5 flex items-center gap-4 opacity-60">
          <div className="w-14 h-14 rounded-full bg-white/10 flex items-center justify-center text-lg font-semibold">
            A
          </div>
          <div className="flex-1">
            <div className="font-medium">Alt Account</div>
            <div className="text-sm text-white/40">Offline Mode</div>
          </div>
        </div>
      </div>
    </div>
  )
}
