package org.mycrimes.insecuretests.payments.create;

enum InputTarget {
  MONEY() {
    @Override
    InputTarget next() {
      return FIAT_MONEY;
    }
  },
  FIAT_MONEY {
    @Override
    InputTarget next() {
      return MONEY;
    }
  };

  abstract InputTarget next();
}
