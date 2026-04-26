"""
Fraud Detection ML Model
Trains a Random Forest classifier on synthetic transaction data.
In production, replace with real labeled training data.
"""

import numpy as np
import pickle
import os
import logging

logger = logging.getLogger(__name__)

MODEL_PATH = "fraud_model.pkl"

FEATURE_NAMES = [
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


class FraudModel:
    def __init__(self):
        self.model = None

    def load_or_train(self):
        if os.path.exists(MODEL_PATH):
            logger.info("Loading pre-trained fraud model from disk...")
            with open(MODEL_PATH, "rb") as f:
                self.model = pickle.load(f)
        else:
            logger.info("No pre-trained model found. Training on synthetic data...")
            self._train()

    def _train(self):
        """
        Train a Random Forest on synthetic data.
        Replace generate_synthetic_data() with real labeled data in production.
        """
        from sklearn.ensemble import RandomForestClassifier
        from sklearn.model_selection import train_test_split
        from sklearn.metrics import classification_report

        X, y = self._generate_synthetic_data(n_samples=10000)
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, random_state=42, stratify=y
        )

        self.model = RandomForestClassifier(
            n_estimators=100,
            max_depth=10,
            class_weight="balanced",  # handle class imbalance
            random_state=42,
            n_jobs=-1,
        )
        self.model.fit(X_train, y_train)

        y_pred = self.model.predict(X_test)
        logger.info("Model training complete:\n" + classification_report(y_test, y_pred))

        with open(MODEL_PATH, "wb") as f:
            pickle.dump(self.model, f)
        logger.info(f"Model saved to {MODEL_PATH}")

    def predict_proba(self, feature_vector: np.ndarray) -> np.ndarray:
        if self.model is None:
            raise RuntimeError("Model not loaded. Call load_or_train() first.")
        return self.model.predict_proba(feature_vector)

    def _generate_synthetic_data(self, n_samples: int = 10000):
        """
        Generate synthetic transaction data for demo/testing.
        Fraud ~5% of transactions (realistic imbalance).
        """
        np.random.seed(42)
        n_fraud = int(n_samples * 0.05)
        n_legit = n_samples - n_fraud

        # Legitimate transactions
        legit = np.column_stack([
            np.random.exponential(500, n_legit),           # amount (mostly small)
            np.random.randint(6, 22, n_legit),             # hour (business hours)
            np.random.randint(1, 6, n_legit),              # day of week (Mon-Fri)
            np.random.randint(90, 1825, n_legit),          # source account age
            np.random.uniform(1000, 50000, n_legit),       # source balance
            np.random.randint(30, 730, n_legit),           # dest account age
            np.random.randint(1, 20, n_legit),             # txns last 30 days
            np.random.exponential(300, n_legit),           # avg txn amount
            np.random.exponential(600, n_legit),           # max txn amount
            np.random.randint(0, 2, n_legit),              # txns last hour
            np.random.binomial(1, 0.2, n_legit).astype(float),  # first time recipient
            np.random.exponential(0.5, n_legit),           # amount deviation
        ])

        # Fraudulent transactions (different distribution)
        fraud = np.column_stack([
            np.random.choice([100, 500, 1000, 5000, 9999], n_fraud),  # round amounts
            np.random.randint(0, 6, n_fraud),              # odd hours (night)
            np.random.randint(6, 8, n_fraud),              # weekends
            np.random.randint(0, 30, n_fraud),             # new source accounts
            np.random.uniform(100, 2000, n_fraud),         # low balance
            np.random.randint(0, 14, n_fraud),             # new dest accounts
            np.random.randint(0, 5, n_fraud),              # few prior txns
            np.random.exponential(200, n_fraud),           # avg txn
            np.random.exponential(1000, n_fraud),          # max txn
            np.random.randint(3, 10, n_fraud),             # many txns last hour
            np.ones(n_fraud),                              # always first time
            np.random.uniform(2, 10, n_fraud),             # high deviation
        ])

        X = np.vstack([legit, fraud])
        y = np.concatenate([np.zeros(n_legit), np.ones(n_fraud)])

        # Shuffle
        idx = np.random.permutation(len(y))
        return X[idx], y[idx]
