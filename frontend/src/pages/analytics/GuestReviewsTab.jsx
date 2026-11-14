import React from 'react';
import { Star, MessageSquare } from 'lucide-react';

export default function GuestReviewsTab({ currentData, reviewFilter, setReviewFilter, filteredReviews }) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Rating Breakdown & Category Scorecards */}
      <div
        className="rounded-2xl p-5 border shadow-sm space-y-4 transition-colors duration-150"
        style={{
          backgroundColor: 'var(--bg-card)',
          borderColor: 'var(--border)',
        }}
      >
        <div className="flex items-center gap-2">
          <div className="p-1 rounded-md bg-amber-50 text-amber-500 border border-amber-100 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-800/40">
            <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
          </div>
          <h2 className="text-base font-bold" style={{ color: 'var(--text-primary)' }}>
            Guest Ratings &amp; Category Scores
          </h2>
        </div>
        <p className="text-xs" style={{ color: 'var(--text-secondary)' }}>
          Star rating distribution and categorized quality index from verified table QR portal submissions.
        </p>

        <div className="flex items-center gap-4 py-2 border-b" style={{ borderColor: 'var(--border)' }}>
          <div className="text-3xl font-black tabular-nums" style={{ color: 'var(--text-primary)' }}>
            {currentData.averageRating}
          </div>
          <div>
            <div className="flex items-center gap-1">
              {[1, 2, 3, 4, 5].map((s) => (
                <Star key={s} className="w-4 h-4 fill-amber-400 text-amber-400" />
              ))}
            </div>
            <div className="text-xs mt-0.5" style={{ color: 'var(--text-muted)' }}>
              Based on {currentData.reviewsCount} verified diner reviews
            </div>
          </div>
        </div>

        {/* Star Histogram */}
        <div className="space-y-2 text-xs">
          <div className="flex items-center gap-2">
            <span className="w-8 text-right font-semibold text-amber-900 dark:text-amber-300">5 ★</span>
            <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
              <div className="h-full bg-amber-400 rounded-full" style={{ width: '68%' }} />
            </div>
            <span className="w-8 text-left text-neutral-400 tabular-nums">68%</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-8 text-right font-semibold text-amber-800 dark:text-amber-300">4 ★</span>
            <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
              <div className="h-full bg-amber-300 rounded-full" style={{ width: '22%' }} />
            </div>
            <span className="w-8 text-left text-neutral-400 tabular-nums">22%</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-8 text-right font-semibold text-sky-800 dark:text-sky-300">3 ★</span>
            <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
              <div className="h-full bg-sky-300 rounded-full" style={{ width: '6%' }} />
            </div>
            <span className="w-8 text-left text-neutral-400 tabular-nums">6%</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-8 text-right font-semibold text-rose-700 dark:text-rose-300">2 ★</span>
            <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
              <div className="h-full bg-rose-300 rounded-full" style={{ width: '3%' }} />
            </div>
            <span className="w-8 text-left text-neutral-400 tabular-nums">3%</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-8 text-right font-semibold text-rose-800 dark:text-rose-300">1 ★</span>
            <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ backgroundColor: 'var(--border)' }}>
              <div className="h-full bg-rose-400 rounded-full" style={{ width: '1%' }} />
            </div>
            <span className="w-8 text-left text-neutral-400 tabular-nums">1%</span>
          </div>
        </div>

        {/* Category Quality Index */}
        <div className="pt-3 border-t space-y-2" style={{ borderColor: 'var(--border)' }}>
          <div className="text-xs font-bold" style={{ color: 'var(--text-primary)' }}>
            Category Experience Scores
          </div>
          <div className="space-y-1.5 text-xs">
            <div className="flex justify-between">
              <span style={{ color: 'var(--text-secondary)' }}>Food Quality &amp; Taste</span>
              <span className="font-bold tabular-nums text-emerald-700 dark:text-emerald-400">4.9 / 5.0</span>
            </div>
            <div className="flex justify-between">
              <span style={{ color: 'var(--text-secondary)' }}>Cleanliness &amp; Hygiene</span>
              <span className="font-bold tabular-nums text-teal-700 dark:text-teal-400">4.8 / 5.0</span>
            </div>
            <div className="flex justify-between">
              <span style={{ color: 'var(--text-secondary)' }}>Ambiance &amp; Lighting</span>
              <span className="font-bold tabular-nums text-indigo-700 dark:text-indigo-400">4.7 / 5.0</span>
            </div>
            <div className="flex justify-between">
              <span style={{ color: 'var(--text-secondary)' }}>Service Speed</span>
              <span className="font-bold tabular-nums text-sky-700 dark:text-sky-400">4.6 / 5.0</span>
            </div>
            <div className="flex justify-between">
              <span style={{ color: 'var(--text-secondary)' }}>Value for Money</span>
              <span className="font-bold tabular-nums text-amber-700 dark:text-amber-400">4.5 / 5.0</span>
            </div>
          </div>
        </div>
      </div>

      {/* Live Guest Review Feed with NLP Sentiment */}
      <div
        className="lg:col-span-2 rounded-2xl p-5 border shadow-sm space-y-4 transition-colors duration-150"
        style={{
          backgroundColor: 'var(--bg-card)',
          borderColor: 'var(--border)',
        }}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <div className="p-1 rounded-md bg-indigo-50 text-indigo-600 border border-indigo-100 dark:bg-indigo-950/40 dark:text-indigo-400 dark:border-indigo-800/40">
                <MessageSquare className="w-4 h-4" />
              </div>
              Verified Guest Feedback Stream (IndicBERT NLP)
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--text-secondary)' }}>
              Real guest reviews tagged by multilingual NLP model into positive, neutral, and critical sentiments.
            </p>
          </div>

          {/* Filter Pills */}
          <div className="flex items-center gap-1 text-xs">
            <button
              type="button"
              onClick={() => setReviewFilter('ALL')}
              className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                reviewFilter === 'ALL'
                  ? 'bg-zinc-900 text-white border-zinc-900 dark:bg-zinc-100 dark:text-zinc-900'
                  : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
              }`}
            >
              All
            </button>
            <button
              type="button"
              onClick={() => setReviewFilter('POSITIVE')}
              className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                reviewFilter === 'POSITIVE'
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-300 dark:bg-emerald-950 dark:text-emerald-200 dark:border-emerald-700'
                  : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
              }`}
            >
              Positive
            </button>
            <button
              type="button"
              onClick={() => setReviewFilter('NEUTRAL')}
              className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                reviewFilter === 'NEUTRAL'
                  ? 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-600'
                  : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
              }`}
            >
              Neutral
            </button>
            <button
              type="button"
              onClick={() => setReviewFilter('NEGATIVE')}
              className={`px-2.5 py-1 rounded-lg font-semibold border transition-colors duration-150 cursor-pointer ${
                reviewFilter === 'NEGATIVE'
                  ? 'bg-rose-50 text-rose-800 border-rose-300 dark:bg-rose-950 dark:text-rose-200 dark:border-rose-700'
                  : 'bg-transparent text-zinc-600 border-zinc-200 dark:border-zinc-800 dark:text-zinc-400'
              }`}
            >
              Critical
            </button>
          </div>
        </div>

        {/* Feedback Cards */}
        <div className="space-y-3">
          {filteredReviews.map((item) => {
            const isPos = item.sentiment === 'POSITIVE';
            const isNeg = item.sentiment === 'NEGATIVE';
            const cardTint = isPos
              ? 'bg-emerald-50/20 border-emerald-200/60 dark:bg-emerald-950/10 dark:border-emerald-800/40'
              : isNeg
              ? 'bg-rose-50/20 border-rose-200/60 dark:bg-rose-950/10 dark:border-rose-800/40'
              : 'border-zinc-200/80 dark:border-zinc-800';

            const badgeStyle = isPos
              ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/40 dark:text-emerald-300 dark:border-emerald-800'
              : isNeg
              ? 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/40 dark:text-rose-300 dark:border-rose-800'
              : 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700';

            return (
              <div
                key={item.feedbackId}
                className={`p-3.5 rounded-xl border space-y-2 transition-colors duration-150 ${cardTint}`}
                style={{
                  backgroundColor: !isPos && !isNeg ? 'var(--bg-main)' : undefined,
                }}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-xs" style={{ color: 'var(--text-primary)' }}>
                      Table {item.tableId}
                    </span>
                    <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                      &bull; {item.guest}
                    </span>
                    <span className="flex items-center font-bold text-xs ml-1" style={{ color: 'var(--text-primary)' }}>
                      {item.rating} <Star className="w-3 h-3 fill-amber-400 text-amber-400 ml-0.5" />
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md border ${badgeStyle}`}>
                      {item.sentiment}
                    </span>
                    <span className="text-[11px]" style={{ color: 'var(--text-muted)' }}>
                      {item.timeAgo}
                    </span>
                  </div>
                </div>

                <p className="text-xs leading-relaxed" style={{ color: 'var(--text-primary)' }}>
                  &ldquo;{item.commentText}&rdquo;
                </p>

                <div className="text-[11px] font-semibold" style={{ color: 'var(--text-muted)' }}>
                  Topic Category: <span style={{ color: 'var(--text-secondary)' }}>{item.category}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
