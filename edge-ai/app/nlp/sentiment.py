"""
RESTROVISION Edge AI — Contextual Sentiment & Topic Classifier (BUG-020 Remediation)
Implements:
- Contextual valence lexicon scoring
- 3-token lookbehind negation tracking with clause boundary resets
- Domain-aware handling of "not good" (negative) and "not bad" (mitigated mild positive/neutral)
- Double-negation cancellation (e.g. "not not good" -> positive)
- Intensifier and diminisher token weighting
- Multi-topic categorization (Food Quality, Service Speed, Cleanliness, Value, Ambience)
- Honest model attribution (sentiment-lexicon-valence-1.1.0) with uncalibrated heuristic scores
"""

import re
import math
from typing import Dict, Any, List, Set, Tuple

# Positive and negative polarized words with baseline weights
VALENCE_LEXICON: Dict[str, float] = {
    # Positive tokens
    "great": 1.0,
    "excellent": 1.2,
    "delicious": 1.2,
    "tasty": 1.0,
    "love": 1.0,
    "loved": 1.0,
    "fast": 0.8,
    "quick": 0.8,
    "polite": 0.9,
    "clean": 0.9,
    "wonderful": 1.2,
    "amazing": 1.3,
    "good": 0.9,
    "fresh": 0.8,
    "friendly": 0.9,
    "attentive": 0.9,
    "prompt": 0.8,
    "superb": 1.2,
    "fantastic": 1.2,
    "pleasant": 0.8,
    "velvety": 0.8,
    "crisp": 0.7,
    "perfect": 1.1,
    "perfectly": 1.1,
    "nice": 0.7,
    "worth": 0.8,
    "enjoyed": 0.9,
    "satisfying": 0.8,
    "flavorful": 0.9,
    "flavourful": 0.9,

    # Negative tokens
    "slow": -1.0,
    "bad": -1.0,
    "terrible": -1.3,
    "cold": -0.8,
    "dirty": -1.2,
    "wait": -0.7,
    "waited": -0.7,
    "waiting": -0.7,
    "rude": -1.2,
    "poor": -1.0,
    "worst": -1.4,
    "delay": -0.9,
    "delayed": -0.9,
    "disappointing": -1.1,
    "disappointed": -1.1,
    "horrible": -1.3,
    "unacceptable": -1.2,
    "overcooked": -0.8,
    "undercooked": -0.9,
    "bland": -0.8,
    "stale": -1.0,
    "expensive": -0.7,
    "overpriced": -0.9,
    "noisy": -0.7,
    "burnt": -0.9,
    "salty": -0.7,
}

# Negation operators
NEGATION_TOKENS: Set[str] = {
    "not", "no", "never", "n't", "neither", "nor", "hardly", "barely",
    "scarcely", "without", "lack", "lacks", "lacking", "don't", "doesn't",
    "didn't", "won't", "wasn't", "weren't", "isn't", "aren't", "cannot", "can't"
}

# Tokens that break negation scope
BOUNDARY_TOKENS: Set[str] = {
    ".", ",", ";", "!", "?", "but", "however", "although", "though", "nevertheless", "yet"
}

# Modifiers
INTENSIFIERS: Dict[str, float] = {
    "very": 1.5,
    "extremely": 1.8,
    "super": 1.5,
    "really": 1.4,
    "incredibly": 1.6,
    "exceptionally": 1.7,
    "absolutely": 1.6,
    "highly": 1.4,
}

DIMINISHERS: Dict[str, float] = {
    "slightly": 0.6,
    "somewhat": 0.7,
    "bit": 0.7,
    "little": 0.7,
    "barely": 0.5,
    "hardly": 0.5,
}

# Topic category keywords
CATEGORY_KEYWORDS: Dict[str, List[str]] = {
    "FOOD_QUALITY": ["food", "taste", "dish", "curry", "spicy", "chicken", "paneer", "naan", "dal", "makhani", "cooked", "flavour", "flavor", "sweet", "dessert"],
    "SERVICE_SPEED": ["wait", "slow", "delay", "quick", "fast", "time", "server", "waiter", "service", "staff", "attentive", "attendance", "order"],
    "CLEANLINESS": ["clean", "dirty", "hygiene", "floor", "table", "washroom", "sanitized", "sanitization", "housekeeping", "neat", "tidy"],
    "VALUE": ["price", "cost", "expensive", "cheap", "bill", "worth", "money", "pricing", "value", "portion"],
    "AMBIENCE": ["ambience", "ambiance", "music", "lighting", "atmosphere", "vibe", "seating", "booth", "noise", "loud", "decor"],
}

def _tokenize(text: str) -> List[str]:
    """
    Splits text into words and punctuation boundary tokens while preserving contractions like don't, n't.
    """
    raw_tokens = re.findall(r"[A-Za-z0-9]+(?:'[A-Za-z]+)?|[.,;!?]", text.lower())
    return raw_tokens

def analyze_sentiment(comment: str) -> Dict[str, Any]:
    """
    Analyzes guest review comment using a contextual valence lexicon with:
    - 3-token lookbehind negation scope
    - Double-negation cancellation
    - Contextual mitigation for 'not bad' / 'not terrible'
    - Multi-topic category assignment
    """
    if not comment or not comment.strip():
        return {
            "sentiment": "NEUTRAL",
            "category": "AMBIENCE",
            "confidence": 0.70,
            "valence_score": 0.0,
            "model_version": "sentiment-lexicon-valence-1.1.0",
            "algorithm": "Contextual Negation Valence Lexicon",
            "confidence_calibrated": False
        }

    tokens = _tokenize(comment)
    total_valence = 0.0
    scored_tokens_count = 0
    token_details = []

    for i, token in enumerate(tokens):
        if token in VALENCE_LEXICON:
            base_weight = VALENCE_LEXICON[token]
            
            # Inspect preceding 3 tokens for negation, modifiers, and boundary resets
            lookbehind = tokens[max(0, i - 3):i]
            
            # Check if any clause boundary intervened between the negation and this word
            # e.g., "Food was not bad, wait was slow" -> comma resets scope
            effective_window = []
            for t in reversed(lookbehind):
                if t in BOUNDARY_TOKENS:
                    break
                effective_window.append(t)
            effective_window.reverse()
            
            # Count negation words in the active lookbehind window
            negations_in_window = [t for t in effective_window if t in NEGATION_TOKENS or t.endswith("n't")]
            neg_count = len(negations_in_window)
            
            # Calculate modifier multiplier
            multiplier = 1.0
            for t in effective_window:
                if t in INTENSIFIERS:
                    multiplier *= INTENSIFIERS[t]
                elif t in DIMINISHERS:
                    multiplier *= DIMINISHERS[t]
            
            if neg_count % 2 == 1:
                # Odd negation (single negation)
                if base_weight < 0:
                    # Negating a negative word (e.g. "not bad", "not terrible", "not dirty")
                    # Mitigates to a mild positive / neutral (+0.50)
                    eff_weight = abs(base_weight) * 0.50 * multiplier
                else:
                    # Negating a positive word (e.g. "not good", "not clean", "not delicious")
                    # Inverts polarity to negative
                    eff_weight = -1.0 * base_weight * multiplier
            elif neg_count > 0 and neg_count % 2 == 0:
                # Even negation (double negation: e.g. "not not good", "not without clean")
                # Double negation cancels out, retains original polarity
                eff_weight = base_weight * multiplier
            else:
                # No negation
                eff_weight = base_weight * multiplier
                
            total_valence += eff_weight
            scored_tokens_count += 1
            token_details.append({"token": token, "base": base_weight, "effective": eff_weight})

    # Normalized score in [-1.0, 1.0]
    if scored_tokens_count > 0:
        normalized_score = total_valence / math.sqrt(total_valence ** 2 + 1.0)
    else:
        normalized_score = 0.0

    # Categorize Sentiment Label
    if normalized_score >= 0.20:
        sentiment = "POSITIVE"
    elif normalized_score <= -0.20:
        sentiment = "NEGATIVE"
    else:
        sentiment = "NEUTRAL"

    # Transparent heuristic confidence (disclaiming probabilistic calibration)
    heuristic_confidence = min(0.98, max(0.65, 0.70 + 0.15 * abs(normalized_score)))

    # Topic classification with frequency-based dominance
    comment_lower = comment.lower()
    category_scores: Dict[str, int] = {}
    for cat, kws in CATEGORY_KEYWORDS.items():
        match_count = sum(1 for kw in kws if kw in comment_lower)
        if match_count > 0:
            category_scores[cat] = match_count

    if category_scores:
        # Select category with highest keyword frequency
        primary_category = max(category_scores.items(), key=lambda x: x[1])[0]
        matched_categories = list(category_scores.keys())
    else:
        primary_category = "AMBIENCE"
        matched_categories = ["AMBIENCE"]

    return {
        "sentiment": sentiment,
        "category": primary_category,
        "confidence": round(heuristic_confidence, 2),
        "valence_score": round(normalized_score, 4),
        "model_version": "sentiment-lexicon-valence-1.1.0",
        "algorithm": "Contextual Negation Valence Lexicon",
        "confidence_calibrated": False,
        "matched_categories": matched_categories or [primary_category]
    }
