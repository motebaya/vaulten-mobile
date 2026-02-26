# Vaulten - Feature Showcase

This document showcases the key features of Vaulten through video demonstrations. Each demo highlights specific functionality and user workflows.

---

## 1. Initial Setup

https://github.com/user-attachments/assets/2f10aa53-6f15-49d7-87f1-1f8e01642f88

**Description:**  
First-time vault creation workflow demonstrating:

- BIP-39 passphrase generation (12-word mnemonic)
- Passphrase confirmation step
- 6-digit PIN setup with numeric keypad
- Optional biometric authentication enrollment
- Complete vault initialization

---

## 2. Adding Platforms

https://github.com/user-attachments/assets/2dd44ed7-8044-41bb-929c-1f72837e457c

**Description:**  
Platform management showing:

- Creating new platforms with name and domain
- Selecting platform types (Social, Gaming, Email, Work, etc.)
- Creating custom platform types
- Reusing existing custom types from dropdown selection
- Platform icon generation from favicon

---

## 3. Adding Credentials

https://github.com/user-attachments/assets/556e540a-555c-4413-8eb1-76293287ccf7

**Description:**  
Credential creation across multiple types:

- Standard credentials (username, password, email)
- Social media credentials (with 2FA, phone, recovery email)
- Crypto wallet credentials (address, private key, seed phrase)
- Google account credentials (full recovery fields)
- Email and username validation with sanitization
- Password strength indicator
- Platform selection and association

---

## 4. Viewing Credentials

https://github.com/user-attachments/assets/89525aa3-0f8b-49ec-b0d7-17f069c24a40

**Description:**  
Secure credential viewing workflow:

- PIN verification with numeric keypad before viewing
- Optional biometric authentication
- Displaying encrypted credential fields
- Copy-to-clipboard for sensitive data
- Password visibility toggle
- Compact modal layout with reduced empty space

---

## 5. Editing Credentials

https://github.com/user-attachments/assets/b8ce3743-4696-4000-98d1-38df664256d9

**Description:**  
Credential modification features:

- Updating username, password, and other fields
- Field validation (email format, username sanitization)
- Password strength meter during edits
- Platform reassignment
- Saving changes with encryption

---

## 6. Deleting Credentials

https://github.com/user-attachments/assets/e9355cb2-8caa-4ff9-9036-ba25ac5a2d81

**Description:**  
Credential deletion with security:

- Delete confirmation dialog
- PIN verification with numeric keypad
- Optional biometric confirmation
- Secure data clearing from database

---

## 7. Editing Platforms

https://github.com/user-attachments/assets/92321edc-bcef-4f0b-891e-3deeb62c0ee3

**Description:**  
Platform modification with restrictions:

- Updating platform name, domain, and type
- 24-hour name edit cooldown enforcement
- Countdown timer display for restricted edits
- Duplicate name/domain validation
- Custom type support

---

## 8. Deleting Platforms

https://github.com/user-attachments/assets/1680c61e-a707-4029-93e8-1ef335b8c783

**Description:**  
Platform deletion workflow:

- Delete confirmation with credential count warning
- PIN verification with numeric keypad
- Optional biometric authentication
- Cascade deletion of associated credentials
- Custom platforms only (default platforms protected)

---

## 9. Dashboard Credential Filters

https://github.com/user-attachments/assets/6a731510-a4b2-46a4-8e76-07d6c0d19fff

**Description:**  
Dashboard filtering and sorting features:

- Search credentials by username, email, or platform name
- Real-time search with keyword filtering
- Sort by name, date created, last accessed
- Ascending/descending sort order
- Filter persistence across sessions

---

## 10. Dashboard Platform Filter

https://github.com/user-attachments/assets/9643a331-9aef-4be7-8763-e8cf578bfdcf

**Description:**  
Platform-based credential filtering:

- Filter credentials by specific platform
- Quick access to platform-specific credentials
- Combined with search and sort options
- Grouped credential display by platform

---

## 11. Exporting Database

https://github.com/user-attachments/assets/d65546cc-014a-4024-89dc-aacd445ece4c

**Description:**  
Encrypted vault backup creation:

- Passphrase verification before export
- Generate encrypted ZIP backup with vault.enc
- Include metadata.json with backup statistics
- Save to device Downloads folder
- Export confirmation with file location
- Keyboard-aware scrollable layout

---

## 13. Importing Database

https://github.com/user-attachments/assets/efab9956-7e60-4485-ab93-0956d886619f

**Description:**  
Vault restoration from backup:

- File picker for selecting backup ZIP
- Backup preview with credential count and date
- Passphrase verification for decryption
- PIN setup for restored vault
- Optional biometric enrollment
- Complete vault restoration and auto-unlock

---

## Technical Features Demonstrated

### Security

- ✅ PIN-based authentication with numeric keypad
- ✅ Biometric authentication (fingerprint/face)
- ✅ BIP-39 passphrase generation
- ✅ AES-256-GCM field encryption
- ✅ Secure credential viewing with PIN gate
- ✅ Auto-lock on screen off and background

### User Experience

- ✅ Material Design 3 with dark mode
- ✅ Keyboard-aware layouts with tap-to-dismiss
- ✅ Real-time search and filtering
- ✅ Password strength indicator
- ✅ Form validation with error messages
- ✅ Username sanitization (reject punctuation except @)
- ✅ Email validation with proper error display

### Data Management

- ✅ Multiple credential types support
- ✅ Platform organization with custom types
- ✅ Encrypted backup export/import
- ✅ 24-hour platform name edit restriction
- ✅ Cascade deletion with warnings
- ✅ Duplicate validation

---

## Repository

For more information, visit the [GitHub repository](https://github.com/motebaya/vault-app-mobile).

---

\_All videos recorded on Android device running Vaulten v2.1.0
