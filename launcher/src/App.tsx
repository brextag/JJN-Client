import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import Sidebar from './components/Sidebar'
import Home from './pages/Home'
import Multiplayer from './pages/Multiplayer'
import Cosmetics from './pages/Cosmetics'
import Mods from './pages/Mods'
import Settings from './pages/Settings'
import Accounts from './pages/Accounts'
import Performance from './pages/Performance'

const pages = {
  home: Home,
  multiplayer: Multiplayer,
  cosmetics: Cosmetics,
  mods: Mods,
  settings: Settings,
  accounts: Accounts,
  performance: Performance,
}

export type PageKey = keyof typeof pages

export default function App() {
  const [activePage, setActivePage] = useState<PageKey>('home')

  const CurrentPage = pages[activePage]

  return (
    <div className="flex h-screen w-screen overflow-hidden">
      <Sidebar active={activePage} onChange={setActivePage} />

      <main className="flex-1 relative overflow-hidden">
        <AnimatePresence mode="wait">
          <motion.div
            key={activePage}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.25, ease: [0.4, 0, 0.2, 1] }}
            className="h-full w-full"
          >
            <CurrentPage />
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  )
}
