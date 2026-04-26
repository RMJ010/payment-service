"""
ML Fraud Detection Service
Provides a REST API for fraud scoring using a trained Random Forest model.
Falls back to rule-based scoring if model is unavailable.
"""

from flask import Flask, request, jsonify
import numpy as np
import logging
import os

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Try to import ML libraries (they may not be installed)
try:
    from model import FraudModel
    model = FraudModel()
    model.load_or_train()
    logger.info("ML model loaded successfully")
    USE_ML = True
except Exception as e:
    logger.warning(f"Could not load ML model, using rule-based scoring: {e}")
    USE_ML = False


FEATURE_ORDER = [
    "amount",
    "hour_of_day",
    "day_of_week",
    "source_account_age_days",
    "source_account_balance",
    "destination_account_age_days",
    "transactions_last_30_days",
    "avg_transaction_amount_30_days",
    "max_transaction_amount_30_days",
    "transactions_last_hour",
    "first_time_recipient",
    "amount_deviation_from_typical",
]


def rule_based_score(features: dict) -> float:
    """
    Heuristic fraud scoring when ML model is unavailable.
    Returns a fraud probability between 0.0 and 1.0.
    """
    score = 0.05

    amount = features.get("amount", 0)
    if amount > 10000:
        score += 0.25
    elif amount > 5000:
        score += 0.12

    txns_last_hour = features.get("transactions_last_hour", 0)
    if txns_last_hour > 5:
        score += 0.30
    elif txns_last_hour > 3:
        score += 0.10

    first_time = features.get("first_time_recipient", 0)
    if first_time == 1.0 and amount > 1000:
        score += 0.15

    account_age = features.get("source_account_age_days", 365)
    if account_age < 7 and amount > 1000:
        score += 0.20

    deviation = features.get("amount_deviation_from_typical", 0)
    if deviation > 5:
        score += 0.15
    elif deviation > 2:
        score += 0.05

    # Round number suspicious signal
    if amount > 0 and amount % 100 == 0 and amount > 500:
        score += 0.05

    return min(score, 1.0)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "healthy", "ml_model_active": USE_ML})


@app.route("/predict", methods=["POST"])
def predict():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No JSON body provided"}), 400

        features = data.get("features", data)  # support both wrapped and flat

        if USE_ML:
            feature_vector = np.array(
                [features.get(f, 0) for f in FEATURE_ORDER]
            ).reshape(1, -1)
            fraud_probability = float(model.predict_proba(feature_vector)[0][1])
        else:
            fraud_probability = rule_based_score(features)

        return jsonify({
            "fraud_probability": fraud_probability,
            "risk_level": _risk_level(fraud_probability),
            "model_used": "ml" if USE_ML else "heuristic",
        })

    except Exception as e:
        logger.error(f"Prediction error: {e}", exc_info=True)
        return jsonify({"error": str(e)}), 500


def _risk_level(score: float) -> str:
    if score < 0.3:
        return "LOW"
    if score < 0.6:
        return "MEDIUM"
    if score < 0.75:
        return "HIGH"
    return "CRITICAL"


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("FLASK_DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug)
