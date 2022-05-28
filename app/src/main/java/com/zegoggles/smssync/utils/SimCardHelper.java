package com.zegoggles.smssync.utils;

import android.content.Context;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import androidx.annotation.RequiresApi;

import com.zegoggles.smssync.App;

public class SimCardHelper {
  public static SimCard[] getSimCards(Context context) {
    if (Build.VERSION.SDK_INT >= 29) return getSimCardsInternal(context); else {
      SimCard[] simCards = new SimCard[1];
      simCards[0] = new SimCard("1", "1");
      return simCards;
    }
  }

  @RequiresApi(29)
  private static SimCard[] getSimCardsInternal(Context context) {
    int simMaxCount = 1;
    try {
      SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(
        Context.TELEPHONY_SUBSCRIPTION_SERVICE
      );
      simMaxCount = subscriptionManager.getActiveSubscriptionInfoCountMax();
    } catch (Exception e) {
      simMaxCount = 1;
    }

    SimCard[] simCards = new SimCard[simMaxCount];

    try {
      SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(
        Context.TELEPHONY_SUBSCRIPTION_SERVICE
      );

      int simCount = 0;
      for (SubscriptionInfo subscriptionInfo : subscriptionManager.getActiveSubscriptionInfoList()) {
        String phoneNumber = transformPhoneNumber(subscriptionInfo.getNumber());
        String iccId = subscriptionInfo.getIccId();
        simCards[simCount] = new SimCard(phoneNumber, iccId);
        simCount++;
      }

      simCards = fillWithFallBack(simCards, simMaxCount);
    } catch (Exception e) {
      simCards = fillWithFallBack(simCards, simMaxCount);
      if (simCards.length == 0) {
        simCards[0] = new SimCard("1", "1");
      }
    }

    return simCards;
  }

  private static String transformPhoneNumber(String number) {
    return number.replaceAll("[^\\d]", "");
  }

  private static SimCard[] fillWithFallBack(SimCard[] simCards, int simMaxCount) {
    int simCount = 0;
    for (int i = 0; i < simMaxCount; i++) {
      if (simCards[simCount] == null) {
        String cnt = String.valueOf(i + 1);
        simCards[simCount] = new SimCard(cnt, cnt);
      }
      simCount++;
    }
    return simCards;
  }

  public static String addSettingsId(String stringWithoutSettingsId, Integer settingsId) {
    if (settingsId == 0) {
        return stringWithoutSettingsId;
    } else {
        return stringWithoutSettingsId + "_" + settingsId;
    }
  }

  public static String addPhoneNumberIfMultiSim(String plain, Integer settingsId) {
    SimCard[] simCards = App.SimCards;
    if (simCards.length < 2) return plain;
    return plain + " ("+simCards[settingsId].PhoneNumber+")";
  }
}
