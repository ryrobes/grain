(ns styles.core
  "CSS styles as Clojure maps - replaces Tailwind utility classes")

;; =============================================================================
;; Color Palette (extracted from custom theme)
;; =============================================================================

(def colors
  {:gray-50 "#f9fafb"
   :gray-100 "#f3f4f6"
   :gray-200 "#e5e7eb"
   :gray-300 "#d1d5db"
   :gray-400 "#9ca3af"
   :gray-500 "#6b7280"
   :gray-600 "#4b5563"
   :gray-700 "#374151"
   :gray-800 "#1f2937"
   :gray-900 "#111827"

   :blue-400 "#60a5fa"
   :blue-500 "#3b82f6"
   :blue-600 "#2563eb"
   :blue-900 "#1e3a8a"

   :green-300 "#86efac"
   :green-400 "#4ade80"
   :green-700 "#15803d"
   :green-900 "#14532d"

   :yellow-300 "#fde047"
   :yellow-400 "#facc15"
   :yellow-700 "#a16207"
   :yellow-900 "#713f12"

   :red-100 "#fee2e2"
   :red-300 "#fca5a5"
   :red-700 "#b91c1c"
   :red-900 "#7f1d1d"

   :purple-400 "#c084fc"
   :purple-600 "#9333ea"
   :purple-900 "#581c87"

   :cyan-400 "#22d3ee"
   :cyan-600 "#0891b2"
   :cyan-900 "#164e63"

   :orange-400 "#fb923c"
   :orange-600 "#ea580c"
   :orange-900 "#7c2d12"

   :light-green-300 "#86efac"
   :light-green-700 "#15803d"
   :light-green-900 "#14532d"})

;; =============================================================================
;; Layout & Structure
;; =============================================================================

;; Display
(def flex {:display "flex"})
(def inline-flex {:display "inline-flex"})
(def block {:display "block"})
(def inline-block {:display "inline-block"})
(def hidden {:display "none"})

;; Flex direction
(def flex-row {:flex-direction "row"})
(def flex-col {:flex-direction "column"})

;; Flex wrap
(def flex-wrap {:flex-wrap "wrap"})
(def flex-nowrap {:flex-wrap "nowrap"})

;; Justify content
(def justify-start {:justify-content "flex-start"})
(def justify-end {:justify-content "flex-end"})
(def justify-center {:justify-content "center"})
(def justify-between {:justify-content "space-between"})
(def justify-around {:justify-content "space-around"})

;; Align items
(def items-start {:align-items "flex-start"})
(def items-end {:align-items "flex-end"})
(def items-center {:align-items "center"})
(def items-baseline {:align-items "baseline"})
(def items-stretch {:align-items "stretch"})

;; Align self
(def self-auto {:align-self "auto"})
(def self-start {:align-self "flex-start"})
(def self-end {:align-self "flex-end"})
(def self-center {:align-self "center"})

;; Flex grow/shrink
(def flex-1 {:flex "1 1 0%"})
(def flex-auto {:flex "1 1 auto"})
(def flex-shrink-0 {:flex-shrink "0"})

;; Position
(def relative {:position "relative"})
(def absolute {:position "absolute"})
(def fixed {:position "fixed"})
(def sticky {:position "sticky"})

;; Overflow
(def overflow-hidden {:overflow "hidden"})
(def overflow-auto {:overflow "auto"})
(def overflow-scroll {:overflow "scroll"})
(def overflow-x-auto {:overflow-x "auto"})
(def overflow-y-auto {:overflow-y "auto"})

;; =============================================================================
;; Spacing (using rem units)
;; =============================================================================

;; Padding - all sides
(def p-0 {:padding "0"})
(def p-1 {:padding "0.25rem"}) ; 4px
(def p-2 {:padding "0.5rem"})  ; 8px
(def p-3 {:padding "0.75rem"}) ; 12px
(def p-4 {:padding "1rem"})    ; 16px
(def p-6 {:padding "1.5rem"})  ; 24px

;; Padding - horizontal
(def px-2 {:padding-left "0.5rem" :padding-right "0.5rem"})
(def px-3 {:padding-left "0.75rem" :padding-right "0.75rem"})
(def px-4 {:padding-left "1rem" :padding-right "1rem"})
(def px-6 {:padding-left "1.5rem" :padding-right "1.5rem"})

;; Padding - vertical
(def py-0-5 {:padding-top "0.125rem" :padding-bottom "0.125rem"})
(def py-1 {:padding-top "0.25rem" :padding-bottom "0.25rem"})
(def py-1-5 {:padding-top "0.375rem" :padding-bottom "0.375rem"})
(def py-2 {:padding-top "0.5rem" :padding-bottom "0.5rem"})
(def py-3 {:padding-top "0.75rem" :padding-bottom "0.75rem"})
(def py-4 {:padding-top "1rem" :padding-bottom "1rem"})

;; Margin - all sides
(def m-0 {:margin "0"})
(def m-auto {:margin "auto"})

;; Margin - horizontal
(def mx-auto {:margin-left "auto" :margin-right "auto"})
(def mx-4 {:margin-left "1rem" :margin-right "1rem"})

;; Margin - left
(def ml-2 {:margin-left "0.5rem"})
(def ml-6 {:margin-left "1.5rem"})
(def ml-auto {:margin-left "auto"})

;; Margin - right
(def mr-2 {:margin-right "0.5rem"})

;; Margin - top
(def mt-1 {:margin-top "0.25rem"})

;; Margin - bottom
(def mb-1 {:margin-bottom "0.25rem"})
(def mb-2 {:margin-bottom "0.5rem"})
(def mb-3 {:margin-bottom "0.75rem"})

;; Gap (for flexbox/grid)
(def gap-2 {:gap "0.5rem"})
(def gap-3 {:gap "0.75rem"})
(def gap-4 {:gap "1rem"})
(def gap-6 {:gap "1.5rem"})

;; =============================================================================
;; Sizing
;; =============================================================================

;; Width
(def w-2 {:width "0.5rem"})
(def w-4 {:width "1rem"})
(def w-8 {:width "2rem"})
(def w-80 {:width "20rem"})
(def w-96 {:width "24rem"})
(def w-full {:width "100%"})
(def w-screen {:width "100vw"})

;; Min width
(def min-w-280 {:min-width "280px"})
(def min-w-320 {:min-width "320px"})

;; Max width
(def max-w-360 {:max-width "360px"})

;; Height
(def h-2 {:height "0.5rem"})
(def h-4 {:height "1rem"})
(def h-10 {:height "2.5rem"})
(def h-full {:height "100%"})
(def h-screen {:height "100vh"})

;; =============================================================================
;; Typography
;; =============================================================================

;; Font family
(def font-outfit {:font-family "'Outfit', ui-sans-serif, system-ui"})
(def font-vt323 {:font-family "'VT323', monospace"})
(def font-fira-code {:font-family "'Fira Code', 'Consolas', 'Monaco', monospace"})
(def font-mono {:font-family "ui-monospace, 'Fira Code', monospace"})

;; Font size
(def text-xs {:font-size "0.75rem" :line-height "1rem"})
(def text-sm {:font-size "0.875rem" :line-height "1.25rem"})
(def text-base {:font-size "1rem" :line-height "1.5rem"})
(def text-lg {:font-size "1.125rem" :line-height "1.75rem"})

;; Font weight
(def font-medium {:font-weight "500"})
(def font-semibold {:font-weight "600"})
(def font-bold {:font-weight "700"})

;; Text color (using palette)
(def text-white {:color "#ffffff"})
(def text-gray-100 {:color (:gray-100 colors)})
(def text-gray-200 {:color (:gray-200 colors)})
(def text-gray-300 {:color (:gray-300 colors)})
(def text-gray-400 {:color (:gray-400 colors)})
(def text-gray-500 {:color (:gray-500 colors)})
(def text-blue-400 {:color (:blue-400 colors)})
(def text-green-400 {:color (:green-400 colors)})
(def text-yellow-300 {:color (:yellow-300 colors)})
(def text-yellow-400 {:color (:yellow-400 colors)})
(def text-red-100 {:color (:red-100 colors)})
(def text-red-400 {:color "#f87171"})
(def text-purple-400 {:color (:purple-400 colors)})
(def text-cyan-400 {:color (:cyan-400 colors)})
(def text-orange-400 {:color (:orange-400 colors)})
(def text-light-green-300 {:color (:light-green-300 colors)})

;; Text alignment
(def text-left {:text-align "left"})
(def text-center {:text-align "center"})
(def text-right {:text-align "right"})

;; Text transform
(def uppercase {:text-transform "uppercase"})
(def lowercase {:text-transform "lowercase"})
(def capitalize {:text-transform "capitalize"})

;; Letter spacing
(def tracking-wide {:letter-spacing "0.025em"})

;; Whitespace
(def whitespace-nowrap {:white-space "nowrap"})
(def whitespace-pre-wrap {:white-space "pre-wrap"})

;; =============================================================================
;; Backgrounds
;; =============================================================================

(def bg-transparent {:background-color "transparent"})
(def bg-gray-700 {:background-color (:gray-700 colors)})
(def bg-gray-800 {:background-color (:gray-800 colors)})
(def bg-gray-900 {:background-color (:gray-900 colors)})
(def bg-blue-600 {:background-color (:blue-600 colors)})
(def bg-blue-900-30 {:background-color "rgba(30, 58, 138, 0.3)"})
(def bg-green-600 {:background-color "#16a34a"})
(def bg-purple-600 {:background-color (:purple-600 colors)})
(def bg-cyan-600 {:background-color (:cyan-600 colors)})
(def bg-red-900 {:background-color (:red-900 colors)})
(def bg-red-100 {:background-color (:red-100 colors)})
(def bg-yellow-900 {:background-color (:yellow-900 colors)})
(def bg-light-green-900 {:background-color (:light-green-900 colors)})
(def bg-green-400 {:background-color (:green-400 colors)})
(def bg-yellow-400 {:background-color (:yellow-400 colors)})

;; Background opacity utilities
(defn bg-opacity [alpha]
  {:background-color (str "rgba(0, 0, 0, " alpha ")")})

;; =============================================================================
;; Borders
;; =============================================================================

;; Border width
(def border {:border "1px solid"})
(def border-2 {:border "2px solid"})
(def border-b {:border-bottom "1px solid"})
(def border-r {:border-right "1px solid"})
(def border-l {:border-left "1px solid"})
(def border-l-4 {:border-left "4px solid"})

;; Border color
(def border-gray-600 {:border-color (:gray-600 colors)})
(def border-gray-700 {:border-color (:gray-700 colors)})
(def border-blue-500 {:border-color (:blue-500 colors)})
(def border-blue-400 {:border-color (:blue-400 colors)})
(def border-green-400 {:border-color (:green-400 colors)})
(def border-green-700 {:border-color (:green-700 colors)})
(def border-yellow-300 {:border-color (:yellow-300 colors)})
(def border-yellow-700 {:border-color (:yellow-700 colors)})
(def border-red-700 {:border-color (:red-700 colors)})
(def border-orange-400 {:border-color (:orange-400 colors)})
(def border-purple-400 {:border-color (:purple-400 colors)})
(def border-cyan-400 {:border-color (:cyan-400 colors)})
(def border-light-green-700 {:border-color (:light-green-700 colors)})

;; Border radius
(def rounded {:border-radius "0.25rem"})
(def rounded-lg {:border-radius "0.5rem"})
(def rounded-full {:border-radius "9999px"})

;; =============================================================================
;; Effects
;; =============================================================================

;; Opacity
(def opacity-50 {:opacity "0.5"})
(def opacity-60 {:opacity "0.6"})

;; Box shadow
(def shadow-lg {:box-shadow "0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)"})

;; =============================================================================
;; Transitions & Animations
;; =============================================================================

(def transition-colors {:transition "color 0.2s, background-color 0.2s, border-color 0.2s"})
(def transition-all {:transition "all 0.2s"})
(def transition-opacity {:transition "opacity 0.2s"})

(def animate-pulse {:animation "pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite"})

;; =============================================================================
;; Cursors & Interaction
;; =============================================================================

(def cursor-pointer {:cursor "pointer"})
(def cursor-default {:cursor "default"})
(def pointer-events-none {:pointer-events "none"})

;; =============================================================================
;; Z-index
;; =============================================================================

(def z-10 {:z-index "10"})
(def z-50 {:z-index "50"})
(def z-1000 {:z-index "1000"})

;; =============================================================================
;; Common Component Patterns
;; =============================================================================

;; Hover states (must be applied via :hover pseudo-class in garden or inline event handlers)
(defn hover-bg-gray-700 []
  {:background-color (:gray-700 colors)})

(defn hover-bg-gray-700-50 []
  {:background-color "rgba(55, 65, 81, 0.5)"})

(defn hover-text-white []
  {:color "#ffffff"})

(defn hover-text-gray-300 []
  {:color (:gray-300 colors)})

;; Button base styles
(def button-base
  (merge px-3 py-1-5 rounded text-xs font-medium transition-all cursor-pointer))

;; Button variants
(def button-primary
  (merge button-base bg-blue-600 text-white shadow-lg))

(def button-secondary
  (merge button-base bg-gray-800 text-gray-400))

;; Card patterns
(def card-base
  (merge p-3 border-b border-gray-700 transition-colors))

;; =============================================================================
;; Utilities
;; =============================================================================

(defn style
  "Merge multiple style maps"
  [& styles]
  (apply merge styles))
