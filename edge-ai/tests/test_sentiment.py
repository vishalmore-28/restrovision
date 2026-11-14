"""
Unit and regression tests for Contextual Sentiment NLP (BUG-020 Remediation).
Tests:
- "not good" vs "not bad" contextual valence
- Double negation cancellation ("not not good", "not without")
- Negation lookbehind scope boundaries and punctuation reset
- Contrastive conjunction scope resets ("but", "however", "although", "yet")
- Intensifiers and diminishers
- Mixed positive and negative clauses
- Unknown or unsupported vocabulary
- Malformed inputs (None, integers, non-strings)
- Multi-topic categorization (Food Quality, Service Speed, Cleanliness, Value, Ambience)
- Honest model metadata and uncalibrated confidence
"""

import pytest
from app.nlp.sentiment import analyze_sentiment

class TestContextualNegation:
    def test_not_good_is_negative(self):
        res = analyze_sentiment("The food was not good.")
        assert res["sentiment"] == "NEGATIVE"
        assert res["valence_score"] < 0
        assert res["confidence_calibrated"] is False

    def test_not_bad_is_mitigated_neutral_or_positive(self):
        res = analyze_sentiment("The dal makhani was not bad.")
        # "not bad" should not be classified as negative; it mitigates to mild positive/neutral
        assert res["sentiment"] in ("POSITIVE", "NEUTRAL")
        assert res["valence_score"] >= 0

    def test_not_clean_is_negative(self):
        res = analyze_sentiment("The table was definitely not clean.")
        assert res["sentiment"] == "NEGATIVE"
        assert res["category"] == "CLEANLINESS"

    def test_not_terrible_is_mitigated(self):
        res = analyze_sentiment("The food was not terrible.")
        assert res["sentiment"] in ("NEUTRAL", "POSITIVE")
        assert res["valence_score"] >= 0

    def test_double_negation_cancellation(self):
        # "not not good" -> negation cancels out, remains positive
        res = analyze_sentiment("The biryani was not not good.")
        assert res["sentiment"] == "POSITIVE"
        assert res["valence_score"] > 0

    def test_double_negation_not_without(self):
        # "not without flavor"
        res = analyze_sentiment("The curry was not without flavor.")
        assert res["sentiment"] in ("POSITIVE", "NEUTRAL")
        assert res["valence_score"] >= 0


class TestNegationScopeAndBoundary:
    def test_boundary_resets_negation_scope_with_comma_and_but(self):
        # "Food was not good, but service was fast and attentive"
        # "not good" is negative, but comma and "but" reset scope so "fast" and "attentive" are positive
        comment = "Food was not good, but service was fast and attentive."
        res = analyze_sentiment(comment)
        # Fast (+0.8) and attentive (+0.9) outweigh negated good (-0.9)
        assert res["sentiment"] in ("POSITIVE", "NEUTRAL")
        assert "SERVICE_SPEED" in res["matched_categories"]

    def test_contrastive_words_reset_negation(self):
        # "The soup was not warm however the bread was fresh"
        comment = "The soup was not warm however the bread was fresh"
        res = analyze_sentiment(comment)
        # "fresh" should be positive, not negated by "not" before "however"
        assert res["valence_score"] >= 0

    def test_negation_does_not_leak_beyond_three_tokens(self):
        # "not" followed by 4 neutral words before "delicious":
        # lookbehind window is 3 tokens, so "delicious" should not be inverted
        comment = "I was not in a hurry today and the curry was delicious."
        res = analyze_sentiment(comment)
        assert res["sentiment"] == "POSITIVE"
        assert res["valence_score"] > 0


class TestMixedClausesAndUnknownVocabulary:
    def test_mixed_positive_and_negative_clauses(self):
        # "The food was delicious, but the wait was terrible."
        # delicious (+1.2), wait (-0.7), terrible (-1.2) -> net negative (-0.7 / sqrt(0.7^2+1) = -0.6247)
        comment = "The food was delicious, but the wait was terrible."
        res = analyze_sentiment(comment)
        assert "FOOD_QUALITY" in res["matched_categories"]
        assert "SERVICE_SPEED" in res["matched_categories"]
        assert res["sentiment"] == "NEGATIVE"
        assert -0.8 <= res["valence_score"] <= -0.5

    def test_unknown_or_unsupported_vocabulary(self):
        # Nonsense or un-lexiconed words
        comment = "xyz qwerty blorp floop zap"
        res = analyze_sentiment(comment)
        assert res["sentiment"] == "NEUTRAL"
        assert res["valence_score"] == 0.0
        assert res["category"] == "AMBIENCE"
        assert res["confidence_calibrated"] is False


class TestIntensifiersAndDiminishers:
    def test_intensifiers_increase_magnitude(self):
        base_res = analyze_sentiment("The food was delicious.")
        intensified_res = analyze_sentiment("The food was extremely delicious.")
        assert intensified_res["valence_score"] > base_res["valence_score"]

    def test_diminishers_decrease_magnitude(self):
        base_res = analyze_sentiment("The soup was cold.")
        diminished_res = analyze_sentiment("The soup was slightly cold.")
        assert abs(diminished_res["valence_score"]) < abs(base_res["valence_score"])


class TestMultiTopicCategorization:
    def test_food_quality_category(self):
        res = analyze_sentiment("The paneer tikka was spicy and very flavorful.")
        assert res["category"] == "FOOD_QUALITY"
        assert res["sentiment"] == "POSITIVE"

    def test_service_speed_category(self):
        res = analyze_sentiment("We experienced an unacceptable delay waiting for our server.")
        assert res["category"] == "SERVICE_SPEED"
        assert res["sentiment"] == "NEGATIVE"

    def test_cleanliness_category(self):
        res = analyze_sentiment("The washroom and dining floor were impeccably clean and sanitized.")
        assert res["category"] == "CLEANLINESS"
        assert res["sentiment"] == "POSITIVE"

    def test_value_category(self):
        res = analyze_sentiment("Extremely expensive bill for tiny portions, not worth the cost.")
        assert res["category"] == "VALUE"
        assert res["sentiment"] == "NEGATIVE"

    def test_ambience_category(self):
        res = analyze_sentiment("Pleasant lighting, wonderful background music and relaxing atmosphere.")
        assert res["category"] == "AMBIENCE"
        assert res["sentiment"] == "POSITIVE"


class TestEmptyAndMalformedInputs:
    def test_empty_and_whitespace(self):
        for empty_in in ["", "   \n\t  "]:
            res = analyze_sentiment(empty_in)
            assert res["sentiment"] == "NEUTRAL"
            assert res["valence_score"] == 0.0
            assert res["confidence_calibrated"] is False

    def test_none_and_non_string_types(self):
        # None, integers, floats, lists
        for bad_in in [None, 12345, 99.9, ["sample"]]:
            res = analyze_sentiment(bad_in)
            assert isinstance(res, dict)
            assert "sentiment" in res
            assert "confidence" in res
            assert res["confidence_calibrated"] is False

    def test_honest_model_metadata(self):
        res = analyze_sentiment("Great food!")
        assert res["model_version"] == "sentiment-lexicon-valence-1.1.0"
        assert res["confidence_calibrated"] is False
        assert "algorithm" in res
        assert "IndicBERT" not in res["model_version"]
