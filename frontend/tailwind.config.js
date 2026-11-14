/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        mono: {
          lightBg: '#F7F7F7',
          lightCard: '#FFFFFF',
          lightSidebar: '#F0F0F0',
          lightBorder: '#E5E5E5',
          lightTextPrimary: '#171717',
          lightTextSec: '#737373',
          lightTextMuted: '#A3A3A3',
          lightControl: '#262626',
          darkBg: '#111111',
          darkCard: '#1A1A1A',
          darkSidebar: '#151515',
          darkBorder: '#303030',
          darkTextPrimary: '#F5F5F5',
          darkTextSec: '#B3B3B3',
          darkTextMuted: '#858585',
          darkControl: '#F5F5F5',
        },
      },
    },
  },
  plugins: [],
}
