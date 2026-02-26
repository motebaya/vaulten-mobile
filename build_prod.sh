#!/bin/bash
# ============================================================================
# Production Build Script for Vaulten App
# ============================================================================
# This script helps you build a production-ready APK
# Run from the project root directory
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Flags
GENERATE_KEYSTORE=false
BUILD_RELEASE=false
BUILD_DEBUG=false
CLEAN=false
SHOW_HELP=false

show_help() {
    echo ""
    echo -e "${CYAN}Vaulten App - Production Build Script${NC}"
    echo -e "${CYAN}====================================${NC}"
    echo ""
    echo -e "${YELLOW}Usage: ./build_prod.sh [options]${NC}"
    echo ""
    echo -e "${GREEN}Options:${NC}"
    echo "  --generate-keystore  Generate a new release keystore"
    echo "  --release            Build production APK (requires keystore)"
    echo "  --debug              Build debug APK"
    echo "  --clean              Clean build directories"
    echo "  --help               Show this help message"
    echo ""
    echo -e "${GREEN}Examples:${NC}"
    echo "  ./build_prod.sh --generate-keystore  # First time setup"
    echo "  ./build_prod.sh --release            # Build production APK"
    echo "  ./build_prod.sh --clean --release    # Clean and build"
    echo ""
}

test_java_installed() {
    if command -v java &> /dev/null; then
        java_version=$(java -version 2>&1 | head -n 1)
        echo -e "${GREEN}Java found: $java_version${NC}"
        return 0
    else
        echo -e "${RED}ERROR: Java is not installed or not in PATH${NC}"
        echo -e "${YELLOW}Please install JDK 17 or later${NC}"
        return 1
    fi
}

generate_keystore() {
    echo ""
    echo -e "${CYAN}Generating Release Keystore${NC}"
    echo -e "${CYAN}============================${NC}"
    echo ""
    
    if [ -f "vault-release.jks" ]; then
        echo -e "${YELLOW}WARNING: vault-release.jks already exists!${NC}"
        read -p "Do you want to overwrite it? (y/N) " confirm
        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            echo -e "${RED}Aborted.${NC}"
            return
        fi
        rm -f "vault-release.jks"
    fi
    
    echo -e "${YELLOW}Please provide the following information:${NC}"
    echo ""
    
    read -s -p "Enter keystore password (min 6 chars): " store_password
    echo ""
    read -s -p "Enter key password (min 6 chars): " key_password
    echo ""
    read -p "Your name (CN): " cn
    read -p "Organization unit (OU): " ou
    read -p "Organization (O): " o
    read -p "City (L): " l
    read -p "State (ST): " st
    read -p "Country code (C, e.g., US): " c
    
    dname="CN=$cn, OU=$ou, O=$o, L=$l, ST=$st, C=$c"
    
    echo ""
    echo -e "${CYAN}Generating keystore...${NC}"
    
    keytool -genkey -v \
        -keystore vault-release.jks \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -alias vault \
        -storepass "$store_password" \
        -keypass "$key_password" \
        -dname "$dname"
    
    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}Keystore generated successfully!${NC}"
        echo ""
        
        # Create keystore.properties
        cat > keystore.properties << EOF
# Keystore configuration for release signing
# Generated on $(date "+%Y-%m-%d %H:%M:%S")
# DO NOT COMMIT THIS FILE TO VERSION CONTROL

storeFile=vault-release.jks
storePassword=$store_password
keyAlias=vault
keyPassword=$key_password
EOF
        
        echo -e "${GREEN}Created keystore.properties${NC}"
        echo ""
        echo -e "${YELLOW}IMPORTANT: Keep these files safe and backed up!${NC}"
        echo -e "${YELLOW}  - vault-release.jks (keystore file)${NC}"
        echo -e "${YELLOW}  - keystore.properties (passwords)${NC}"
        echo ""
        echo -e "${CYAN}These files are in .gitignore and will NOT be committed.${NC}"
    else
        echo -e "${RED}ERROR: Failed to generate keystore${NC}"
    fi
}

build_release() {
    echo ""
    echo -e "${CYAN}Building Release APK${NC}"
    echo -e "${CYAN}====================${NC}"
    echo ""
    
    # Check for keystore
    if [ ! -f "keystore.properties" ]; then
        echo -e "${RED}ERROR: keystore.properties not found!${NC}"
        echo ""
        echo -e "${YELLOW}Run with --generate-keystore first to create signing keys${NC}"
        echo -e "${YELLOW}Or copy keystore.properties.template to keystore.properties and fill in values${NC}"
        return 1
    fi
    
    if [ ! -f "vault-release.jks" ]; then
        echo -e "${RED}ERROR: vault-release.jks not found!${NC}"
        echo -e "${YELLOW}Run with --generate-keystore first to create the keystore${NC}"
        return 1
    fi
    
    echo -e "${CYAN}Starting release build...${NC}"
    echo ""
    
    ./gradlew assembleRelease --no-daemon
    
    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}Build completed successfully!${NC}"
        echo ""
        echo -e "${CYAN}APK location:${NC}"
        echo -e "${YELLOW}  app/build/outputs/apk/release/app-release.apk${NC}"
        echo ""
        
        # Show APK info
        apk_path="app/build/outputs/apk/release/app-release.apk"
        if [ -f "$apk_path" ]; then
            apk_size=$(du -h "$apk_path" | cut -f1)
            echo -e "${GREEN}APK Size: $apk_size${NC}"
        fi
    else
        echo ""
        echo -e "${RED}ERROR: Build failed!${NC}"
        return 1
    fi
}

build_debug() {
    echo ""
    echo -e "${CYAN}Building Debug APK${NC}"
    echo -e "${CYAN}==================${NC}"
    echo ""
    
    ./gradlew assembleDebug --no-daemon
    
    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}Build completed successfully!${NC}"
        echo ""
        echo -e "${CYAN}APK location:${NC}"
        echo -e "${YELLOW}  app/build/outputs/apk/debug/app-debug.apk${NC}"
    else
        echo ""
        echo -e "${RED}ERROR: Build failed!${NC}"
        return 1
    fi
}

clean_build() {
    echo ""
    echo -e "${CYAN}Cleaning build directories...${NC}"
    
    ./gradlew clean --no-daemon
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Clean completed!${NC}"
    fi
}

# ============================================================================
# MAIN
# ============================================================================

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --generate-keystore)
            GENERATE_KEYSTORE=true
            shift
            ;;
        --release)
            BUILD_RELEASE=true
            shift
            ;;
        --debug)
            BUILD_DEBUG=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help|-h)
            SHOW_HELP=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# Show help if no options or help requested
if $SHOW_HELP || { ! $GENERATE_KEYSTORE && ! $BUILD_RELEASE && ! $BUILD_DEBUG && ! $CLEAN; }; then
    show_help
    exit 0
fi

# Check Java
if ! test_java_installed; then
    exit 1
fi

# Execute requested actions
if $CLEAN; then
    clean_build
fi

if $GENERATE_KEYSTORE; then
    generate_keystore
fi

if $BUILD_RELEASE; then
    build_release
fi

if $BUILD_DEBUG; then
    build_debug
fi

echo ""
echo -e "${GREEN}Done!${NC}"
