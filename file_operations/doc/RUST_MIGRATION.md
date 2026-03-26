# C++ to Rust Migration Guide

This document outlines the migration from C++ to Rust in the `file_operations` module using Mozilla's Rust Android Gradle plugin.

## Overview

The migration replaces the C++ implementation in `src/main/c/rootoperations.c` with a Rust implementation while maintaining the same JNI interface for seamless integration with the existing Kotlin code.

## What Was Migrated

### Original C++ Code
- **File**: `src/main/c/rootoperations.c`
- **Function**: `Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_isDirectory`
- **Purpose**: Checks if a given path is a directory using the `stat()` system call
- **Build System**: CMake

### New Rust Code
- **File**: `src/lib.rs`
- **Function**: Same JNI function signature
- **Purpose**: Identical functionality using Rust's `std::fs::metadata()`
- **Build System**: Cargo with Mozilla's Rust Android Gradle plugin

## Prerequisites

1. **Rust Installation**: Install Rust from https://rustup.rs/
2. **Android NDK**: Install via Android Studio → Tools → SDK Manager → SDK Tools → NDK
3. **Environment Variables**: Set `ANDROID_HOME` or `ANDROID_NDK_ROOT`
4. **Rust-compatible IDE**: Before Android Studio gets first-class Rust support, consider using [Visual Studio Code](https://code.visualstudio.com) with Rust extensions for coding.

## Setup Instructions

### 1. Automated Setup (Recommended)

The project includes an automated setup script that detects your system configuration and generates the correct configuration files:

```bash
cd file_operations
./setup_rust_android.sh
```

This script will:
- **Auto-detect your operating system** (macOS, Linux, Windows)
- **Find your Android SDK** path (from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or common locations)
- **Detect the latest NDK version** installed
- **Extract API level** from your `build.gradle`
- **Install necessary Rust targets** for Android
- **Generate `.cargo/config.toml`** with correct linker paths

### 2. Manual Setup (If Needed)

If the automated setup doesn't work, you can manually configure:

1. **Install Android Rust targets:**
   ```bash
   rustup target add aarch64-linux-android
   rustup target add armv7-linux-androideabi
   rustup target add i686-linux-android
   rustup target add x86_64-linux-android
   ```

2. **Create `.cargo/config.toml`** based on your system:
   - Copy `.cargo/config.toml.template` to `.cargo/config.toml`
   - Replace placeholders with your actual paths:
     - `{{NDK_PATH}}`: Path to your NDK toolchain binaries
     - `{{API_LEVEL}}`: Your minimum API level (e.g., 21)
     - `{{EXE_SUFFIX}}`: `.exe` for Windows, empty for Unix systems

### 3. System Requirements

**Android SDK/NDK:**
- Android SDK installed (via Android Studio or command-line tools)
- NDK installed (via SDK Manager)
- Set `ANDROID_HOME` environment variable

**Supported Platforms:**
- macOS (`darwin-x86_64`)
- Linux (`linux-x86_64`)
- Windows (`windows-x86_64`)

### 4. Build Configuration

The `build.gradle` file now includes:
- Mozilla's Rust Android Gradle plugin
- Cargo configuration for cross-compilation
- Automatic building of Rust code during Android build

## File Structure

```
file_operations/
├── src/
│   └── main/
│       └── rust/
│           ├── lib.rs                 # Main library file (module organizer)
│           └── rootoperations.rs      # Root operations functions
├── .cargo/
│   ├── config.toml           # Auto-generated Cargo configuration for Android
│   └── config.toml.template  # Template for generating config.toml
├── Cargo.toml                # Rust project configuration
├── setup_rust_android.sh     # Automated setup script
├── build.gradle              # Updated with Rust plugin
└── RUST_MIGRATION.md         # This file
```

## Key Changes

### 1. Build System
- **Before**: CMake with `externalNativeBuild`
- **After**: Cargo with Mozilla's Rust Android Gradle plugin

### 2. Dependencies
- **Added**: `jni`, `libc`, `android_logger`, `log` crates
- **Removed**: CMake build configuration

### 3. Function Implementation
- **Before**: C function using `stat()` system call
- **After**: Rust function using `std::fs::metadata()`

### 4. Error Handling
- **Before**: errno-based error handling
- **After**: Rust's `Result` type with proper error handling

### 5. Modular Structure
- **Before**: Single C file with all functions
- **After**: Organized module structure:
  - `lib.rs`: Main library file that organizes and re-exports modules
  - `rootoperations.rs`: Root-specific operations (migrated from C)

## Testing

The Rust implementation includes unit tests:
```bash
cd file_operations
cargo test
```

## Building

The Rust code is automatically built when you build the Android project:
```bash
./gradlew :file_operations:build
```

## Advantages of the Migration

1. **Memory Safety**: Rust prevents memory leaks and buffer overflows
2. **Better Error Handling**: Rust's type system catches errors at compile time
3. **Cross-Platform**: Easier to port to other platforms
4. **Modern Tooling**: Cargo package manager and ecosystem
5. **Performance**: Comparable performance to C++ with better safety

## Troubleshooting

### Configuration Issues
First, try re-running the automated setup script:
```bash
cd file_operations
./setup_rust_android.sh
```

This will re-detect your system configuration and regenerate `.cargo/config.toml`.

### NDK Path Issues
If the automated setup fails to find your NDK, ensure your Android SDK is properly configured:
```bash
export ANDROID_HOME=/path/to/android/sdk
# The script will automatically find the NDK in $ANDROID_HOME/ndk/[version]
```

### Linker Not Found
If you see linker errors, verify your NDK installation:
1. Check that NDK is installed via Android Studio → SDK Manager → SDK Tools → NDK
2. The setup script will automatically use the latest NDK version found
3. If needed, manually verify the generated `.cargo/config.toml` paths exist

### Build Errors
Clean and rebuild:
```bash
cd file_operations
cargo clean
cd ..
./gradlew clean
./gradlew :file_operations:build
```

### Cross-Platform Issues
The setup script supports:
- **macOS**: Automatically detects `darwin-x86_64` platform
- **Linux**: Automatically detects `linux-x86_64` platform  
- **Windows**: Automatically detects `windows-x86_64` platform and adds `.exe` suffix

If you encounter platform-specific issues, check the generated linker paths in `.cargo/config.toml`.

## Extending the Codebase

The modular structure makes it easy to add new functionality:

### Adding a New Module

1. **Create a new `.rs` file** in `src/main/rust/` (e.g., `network_ops.rs`)
2. **Add the module declaration** in `lib.rs`:
   ```rust
   pub mod network_ops;
   ```
3. **Re-export functions** in `lib.rs` for easy access:
   ```rust
   pub use network_ops::{
       download_file,
       upload_file,
   };
   ```

### Adding JNI Functions

To add new JNI functions that can be called from Kotlin:

1. **Add the function** in the appropriate module:
   ```rust
   #[no_mangle]
   pub extern "system" fn Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_newFunction(
       env: JNIEnv,
       _class: JClass,
       param: JString,
   ) -> jboolean {
       // Implementation
   }
   ```

2. **Add the corresponding method** in `NativeOperations.kt`:
   ```kotlin
   @JvmStatic
   external fun newFunction(param: String?): Boolean
   ```

### Example: Adding Compression Operations

```rust
// In src/main/rust/compression_ops.rs
pub mod compression_ops {
    use jni::objects::{JClass, JString};
    use jni::JNIEnv;
    use jni::sys::jboolean;
    
    #[no_mangle]
    pub extern "system" fn Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_compressFile(
        env: JNIEnv,
        _class: JClass,
        source: JString,
        destination: JString,
    ) -> jboolean {
        // Implementation
    }
}
```

## Future Enhancements

1. **Add More Functions**: Migrate additional C++ functions to Rust
2. **Async Operations**: Use Rust's async/await for non-blocking operations
3. **Better Logging**: Enhanced logging with structured logs
4. **Performance Monitoring**: Add metrics collection
5. **Cross-Platform Support**: Extend to other platforms (iOS, Windows, etc.)
6. **Error Types**: Create custom error types for better error handling
7. **Benchmarking**: Add performance benchmarks for critical operations

## Resources

- [Mozilla Rust Android Gradle Plugin](https://github.com/mozilla/rust-android-gradle)
- [JNI for Rust](https://docs.rs/jni/latest/jni/)
- [Android NDK Documentation](https://developer.android.com/ndk)
- [Rust Cross-Compilation](https://rust-lang.github.io/rustup/cross-compilation.html)

## Support

For questions or issues with the migration, please refer to:
- [Mozilla Rust Android Gradle Issues](https://github.com/mozilla/rust-android-gradle/issues)
- [Rust Android Development](https://mozilla.github.io/firefox-browser-architecture/experiments/2017-09-21-rust-on-android.html) 