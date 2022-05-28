package com.zegoggles.smssync.utils;

public class SimCard
{
  public SimCard(String phoneNumber, String iccId) {
    PhoneNumber = phoneNumber;
    IccId = iccId;
  }

  public String PhoneNumber;
  public String IccId;
}