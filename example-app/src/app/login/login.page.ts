import { Component, OnInit } from '@angular/core';
import { PushApp } from 'pushapp-ionic';
import { NavController } from '@ionic/angular';
import { slackWebhookInitOption } from '../../environments/slack-webhook';
import { initializeAndRegisterPushApp } from '../pushapp-setup';

@Component({
  selector: 'app-login',
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss'],
  standalone: false
})
export class LoginPage implements OnInit {

  username = '';
  password = ''; // optional for now

  constructor(private navCtrl: NavController) {}

  private static randomExpiryTimestampMoreThan5Years(): number {
    const now = Date.now();
    const minFuture = now + 365 * 5 * 24 * 60 * 60 * 1000;
    const maxFuture = now + 365 * 10 * 24 * 60 * 60 * 1000;
    const randomMs = minFuture + Math.random() * (maxFuture - minFuture);
    return Math.floor(randomMs / 1000); // seconds
  }

  private static randomDobTimestamp(minAge = 18, maxAge = 60): number {
    const now = new Date();
    const minDate = new Date(now.getFullYear() - maxAge, now.getMonth(), now.getDate()).getTime();
    const maxDate = new Date(now.getFullYear() - minAge, now.getMonth(), now.getDate()).getTime();
    return Math.floor(minDate + Math.random() * (maxDate - minDate)); // ms timestamp
  }

  private static randomGender(): 'male' | 'female' {
    return Date.now() % 2 === 0 ? 'male' : 'female';
  }

  async ngOnInit() {
    // Check if user is already logged in
    const savedUsername = localStorage.getItem('username');
    if (savedUsername) {
      // User is already logged in, redirect to dashboard
      this.navCtrl.navigateRoot('/home');
      return;
    }

    console.log("Login page loaded → Initializing SDK...");

    try {
      await initializeAndRegisterPushApp({
        appId: "demo_1763369170735",
        sandbox: true,
        debugMode: true,
        ...slackWebhookInitOption(),
      });

      console.log("SDK Initialized and device registered");
    } catch (err) {
      console.error("Initialize error:", err);
    }
  }

  async login() {
    if (!this.username) {
      alert("Username is required");
      return;
    }

    try {
      console.log("Calling plugin login...");

      const res = await PushApp.login({
        userId: this.username
      });

      console.log("Login response:", res);

      // Create/update customer profile after login (code = userId_deviceId)
      try {
        const headers = await PushApp.getDeviceHeaders();
        const deviceId = headers['X-Device-ID'] ?? '';
        const code = `${this.username}_${deviceId}`;
        const additionalInfo = {
          expiry_date: LoginPage.randomExpiryTimestampMoreThan5Years(),
          dob: LoginPage.randomDobTimestamp(),
          gender: LoginPage.randomGender(),
        };
        const cohorts = {
          login_type: 'password',
          username_present: true,
          user_segment: 'test_user',
        };
        console.log("Saving customer profile for code:", code);
        await PushApp.saveUserData({
          code,
          additionalInfo,
          cohorts,
        });
      } catch (profileErr) {
        console.warn("Customer profile update failed (non-blocking):", profileErr);
      }

      // Store username in localStorage for dashboard
      localStorage.setItem('username', this.username);

      // Navigate to home page (after login)
      this.navCtrl.navigateRoot('/home');

    } catch (err) {
      console.error("Login failed:", err);
      alert("Login failed");
    }
  }
}
