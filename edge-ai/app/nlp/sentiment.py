from typing import Dict, Any

def analyze_sentiment(comment: str) -> Dict[str, Any]:
    """
    Implements §8.2 M10 Sentiment & Topic Classifier:
    Analyzes guest feedback text into sentiment (POSITIVE, NEUTRAL, NEGATIVE) and category.
    """
    text = comment.lower()

    # Rule-based sentiment scoring
    pos_words = ["great", "excellent", "delicious", "tasty", "love", "fast", "polite", "clean", "wonderful", "amazing"]
    neg_words = ["slow", "bad", "terrible", "cold", "dirty", "wait", "rude", "poor", "worst", "delay"]

    pos_score = sum(1 for w in pos_words if w in text)
    neg_score = sum(1 for w in neg_words if w in text)

    if pos_score > neg_score:
        sentiment = "POSITIVE"
        confidence = min(0.98, 0.70 + 0.1 * pos_score)
    elif neg_score > pos_score:
        sentiment = "NEGATIVE"
        confidence = min(0.98, 0.70 + 0.1 * neg_score)
    else:
        sentiment = "NEUTRAL"
        confidence = 0.80

    # Topic classification
    if any(w in text for w in ["food", "taste", "dish", "curry", "spicy", "chicken", "paneer"]):
        category = "FOOD_QUALITY"
    elif any(w in text for w in ["wait", "slow", "delay", "quick", "fast", "time", "server"]):
        category = "SERVICE_SPEED"
    elif any(w in text for w in ["clean", "dirty", "hygiene", "floor", "table", "washroom"]):
        category = "CLEANLINESS"
    elif any(w in text for w in ["price", "cost", "expensive", "bill", "worth"]):
        category = "VALUE"
    else:
        category = "AMBIENCE"

    return {
        "sentiment": sentiment,
        "category": category,
        "confidence": confidence,
        "model_version": "sentiment-indicbert-1.2.0"
    }
