# CoolConnect - Customer Side System Flowchart

This document details the user journey, screen navigation, and state transitions for the **Customer Side** of the CoolConnect Android application.

---

## 1. Overall Customer App Navigation Flow

```mermaid
flowchart TD
    Start([App Launch]) --> AuthCheck{Is User Logged In?}
    
    AuthCheck -- No --> LoginScreen[Login / Registration Screen]
    LoginScreen --> AuthSuccess{Authenticated?}
    AuthSuccess -- Yes --> OnboardingCheck{Completed Onboarding?}
    AuthSuccess -- No --> LoginScreen
    
    AuthCheck -- Yes --> OnboardingCheck
    
    OnboardingCheck -- No --> OnboardingScreen[Customer Onboarding]
    OnboardingScreen --> HomeTab
    
    OnboardingCheck -- Yes --> MainDashboard[Customer Main Screen]
    
    MainDashboard --> Tabs{Bottom Navigation}
    Tabs --> Tab1[Home Tab]
    Tabs --> Tab2[Services Tab]
    Tabs --> Tab3[Shop Tab]
    Tabs --> Tab4[Track Tab]
    Tabs --> Tab5[Messages Tab]
    Tabs --> Tab6[Profile / You Tab]
```

---

## 2. Detailed Sub-Flows

### A. Service Request & Booking Flow (Services Tab)

```mermaid
flowchart TD
    ServicesTab[Services Tab] --> SelectService[Select HVAC Service Type]
    SelectService --> ServiceType{Service Category}
    
    ServiceType --> |Cleaning / Maintenance| Form[Fill Request Details]
    ServiceType --> |Repair / Troubleshooting| Form
    ServiceType --> |Installation| Form
    
    Form --> AddAddress[Select / Enter Service Address]
    AddAddress --> AddSchedule[Select Date & Preferred Time Slot]
    AddSchedule --> AddNotes[Add Special Instructions / Issue Notes]
    
    AddNotes --> ReviewBooking[Review Service Booking Summary]
    ReviewBooking --> SubmitRequest[Submit Service Request]
    
    SubmitRequest --> WriteFirestore[(Save to Firestore 'service_requests')]
    WriteFirestore --> StatusPending[Status: Pending Admin / Technician Assignment]
    StatusPending --> NavTrack[Navigate to Track Tab]
```

---

### B. E-Commerce & Product Purchase Flow (Shop Tab)

```mermaid
flowchart TD
    ShopTab[Shop Tab] --> Browse[Browse Aircon Units & Parts Catalog]
    Browse --> SearchFilter[Search / Filter Products]
    SearchFilter --> ProductDetail[View Product Specifications & Price]
    
    ProductDetail --> AddCart[Add to Cart]
    AddCart --> CartScreen[View Shopping Cart]
    
    CartScreen --> InstallOption{Include Professional Installation?}
    InstallOption -- Yes --> AttachInstallFee[Add Installation Package & Schedule]
    InstallOption -- No --> DirectOrder[Standard Delivery Order]
    
    AttachInstallFee --> Checkout[Proceed to Checkout]
    DirectOrder --> Checkout
    
    Checkout --> AddressPayment[Confirm Delivery Address & Payment Method]
    AddressPayment --> PlaceOrder[Place Order]
    
    PlaceOrder --> OrderCreated[(Create Document in 'orders')]
    OrderCreated --> StockUpdate[(Update Product Inventory)]
    StockUpdate --> OrderConfirmation[Order Placed Successfully]
```

---

### C. Service Tracking & Real-Time Monitoring Flow (Track Tab)

```mermaid
flowchart TD
    TrackTab[Track Tab] --> FetchActive[Fetch Active Jobs & Requests]
    FetchActive --> HasActive{Active Service/Order Found?}
    
    HasActive -- No --> EmptyState[Display 'No Active Requests']
    
    HasActive -- Yes --> JobStatusCard[Display Active Job Card]
    JobStatusCard --> StatusSwitch{Job Status}
    
    StatusSwitch --> |Pending / Assigned| Waiting[Show Tech Details & Estimated Time]
    StatusSwitch --> |In Progress| LiveMap[Enable Live Location & Map Route]
    StatusSwitch --> |Completed| CompletionProof[View Technician Work Notes & Signature]
    
    CompletionProof --> ReviewDialog[Prompt Rating & Review]
    ReviewDialog --> SubmitReview[(Save Rating to Firestore)]
    SubmitReview --> MoveHistory[Move to History / Completed]
```

---

### D. Messaging & Support Flow (Messages Tab)

```mermaid
flowchart TD
    MsgTab[Messages Tab] --> SelectChat{Select Chat Target}
    
    SelectChat --> |Admin / Customer Support| SupportChat[Open Support Chat Room]
    SelectChat --> |Assigned Technician| TechChat[Open Technician Chat Room]
    
    SupportChat --> MessageInput[Type Message / Attach Image]
    TechChat --> MessageInput
    
    MessageInput --> SendMessage[Send Message]
    SendMessage --> FirestoreChat[(Write to 'chats_v2' Sub-collection)]
    FirestoreChat --> RealtimeUpdate[Update Chat UI via Firestore Listener]
```

---

### E. Account & Settings Flow (Profile / You Tab)

```mermaid
flowchart TD
    ProfileTab[Profile Tab] --> ViewProfile[View User Profile & Information]
    
    ViewProfile --> Option1[Manage Saved Addresses]
    ViewProfile --> Option2[View Order & Service History]
    ViewProfile --> Option3[Privacy & Data Consent Settings]
    ViewProfile --> Option4[Contact Support]
    ViewProfile --> Option5[Logout]
    
    Option5 --> ConfirmLogout{Confirm Logout?}
    ConfirmLogout -- Yes --> ClearAuth[Sign Out from FirebaseAuth & Local State]
    ClearAuth --> RedirectLogin[Redirect to Login Screen]
```

---

## 3. Customer State & Tab Summary

| Tab | Key Components | Primary Functions |
|---|---|---|
| **Home** | Recommended Services, Quick Banners, Maintenance Reminders | Overview of services, quick access to booking & active job banners. |
| **Services** | Category Picker, Address Selector, Schedule Picker | Booking HVAC cleaning, repair, or installation services. |
| **Shop** | Product Catalog, Cart, Checkout Dialog | Purchasing air conditioning units, spare parts, with optional installation. |
| **Track** | Map View, Technician Location Marker, Status Stepper, Rating Dialog | Real-time tracking of technician, ETA, completion proof, and reviews. |
| **Messages** | Message List, Chat Bubble View, Image Attachments | Communication with customer support and assigned technicians. |
| **Profile** | User Information, Service History, Address Book, Logout | Profile editing, order history logs, address management, app settings. |
