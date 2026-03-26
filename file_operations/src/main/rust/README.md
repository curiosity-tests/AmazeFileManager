# Rust File Operations Module

This directory contains the Rust implementation of file operations for the Amaze File Manager, which
was migrated from C/C++ to Rust for more modern, maintainable more safe code, with Rust's inherent
type-safety and memory-safety features.

Please refer to [RUST_MIGRATION.md](../doc/RUST_MIGRATION.md) for detailed information
about the migration process, key changes, and advantages of using Rust.

## Module Structure

The code is organized into separate modules for better maintainability and extensibility:

### `lib.rs` - Main Library File
- **Purpose**: Organizes and re-exports functionality from various modules
- **Content**: Module declarations and re-exports for easy access
- **Note**: This is the entry point for the Rust library

### `rootoperations.rs` - Root Operations
- **Purpose**: Contains root-specific file operations (migrated from C++)
- **Functions**:
  - `Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_isDirectory` - JNI function for directory checking
  - `check_is_directory` - Helper function to check if a path is a directory
  - `check_path_exists` - Helper function to check if a path exists
  - `check_is_file` - Helper function to check if a path is a file
  - `get_file_size` - Helper function to get file size

## Usage

### From Kotlin/Java
The JNI functions are automatically exported and can be called from Kotlin:

```kotlin
// In NativeOperations.kt
@JvmStatic
external fun isDirectory(path: String?): Boolean

// Usage
val isDir = NativeOperations.isDirectory("/some/path")
```

### From Rust (Internal)
Functions can be called directly within the Rust codebase:

```rust
use crate::rootoperations::check_is_directory;

let is_dir = check_is_directory("/some/path")?;
```

### Through Re-exports
Functions can be accessed through the main library:

```rust
use crate::check_is_directory;

let is_dir = check_is_directory("/some/path")?;
```

## Adding New Functionality

### 1. Create a New Module
Create a new `.rs` file (e.g., `network_ops.rs`):

```rust
// network_ops.rs
pub fn download_file(url: &str, destination: &str) -> Result<(), Box<dyn std::error::Error>> {
    // Implementation
    Ok(())
}
```

### 2. Add Module Declaration
In `lib.rs`, add the module declaration:

```rust
pub mod network_ops;
```

### 3. Re-export Functions (Optional)
In `lib.rs`, add re-exports for easy access:

```rust
pub use network_ops::{
    download_file,
    upload_file,
};
```

### 4. Add JNI Functions (If Needed)
If you need to call the function from Kotlin:

```rust
// In network_ops.rs
#[no_mangle]
pub extern "system" fn Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_downloadFile(
    env: JNIEnv,
    _class: JClass,
    url: JString,
    destination: JString,
) -> jboolean {
    // Convert JString to Rust String
    let url_str: String = env.get_string(url).unwrap().into();
    let dest_str: String = env.get_string(destination).unwrap().into();
    
    // Call the actual function
    match download_file(&url_str, &dest_str) {
        Ok(_) => 1, // true
        Err(_) => 0, // false
    }
}
```

Then add the corresponding method in `NativeOperations.kt`:

```kotlin
@JvmStatic
external fun downloadFile(url: String?, destination: String?): Boolean
```

## Testing

Each module includes its own tests. Run tests with:

```bash
cd file_operations
cargo test
```

To test a specific module:

```bash
cargo test rootoperations::tests
```

## Best Practices

1. **Separation of Concerns**: Keep related functions in the same module
2. **Error Handling**: Use `Result<T, E>` for functions that can fail
3. **Documentation**: Add doc comments to all public functions
4. **Testing**: Include unit tests for all functions
5. **JNI Safety**: Always handle JNI string conversion errors
6. **Logging**: Use the `log` crate for debugging information

## Example: Complete Function Implementation

```rust
// In your_module.rs
use jni::objects::{JClass, JString};
use jni::JNIEnv;
use jni::sys::jboolean;

/// Check if a file is executable
pub fn is_executable(path: &str) -> Result<bool, Box<dyn std::error::Error>> {
    let path = std::path::Path::new(path);
    let metadata = path.metadata()?;
    
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        Ok(metadata.permissions().mode() & 0o111 != 0)
    }
    
    #[cfg(not(unix))]
    {
        // On non-Unix systems, check if it's a file with executable extension
        Ok(path.extension()
            .and_then(|ext| ext.to_str())
            .map(|ext| matches!(ext.to_lowercase().as_str(), "exe" | "bat" | "cmd"))
            .unwrap_or(false))
    }
}

/// JNI function to check if a file is executable
#[no_mangle]
pub extern "system" fn Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_isExecutable(
    env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jboolean {
    let path_str = match env.get_string(path) {
        Ok(java_str) => java_str,
        Err(_) => {
            log::error!("Failed to get string from JString");
            return 0;
        }
    };
    
    let path_string: String = path_str.into();
    
    match is_executable(&path_string) {
        Ok(is_exec) => if is_exec { 1 } else { 0 },
        Err(e) => {
            log::error!("Error checking if file is executable: {}", e);
            0
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_is_executable() {
        // Test with a known executable (on Unix systems)
        #[cfg(unix)]
        assert!(is_executable("/bin/ls").unwrap_or(false));
        
        // Test with a non-executable file
        assert!(!is_executable("/etc/passwd").unwrap_or(true));
    }
}
```

This structure makes the codebase highly maintainable and easy to extend with new functionality while keeping everything organized and well-tested. 