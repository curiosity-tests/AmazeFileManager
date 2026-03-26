use jni::objects::{JClass, JString};
use jni::JNIEnv;
use jni::sys::jboolean;
use std::path::Path;
use std::sync::Once;

#[cfg(target_os = "android")]
use android_logger::Config;

#[cfg(target_os = "android")]
use log;

// Per Copilot review suggested, use Once::new to make Android logging init only once
static INIT: Once = Once::new();

/// Initialize Android logging (optional, but useful for debugging)
#[cfg(target_os = "android")]
fn init_logging() {
    INIT.call_once(|| {
        android_logger::init_once(
            Config::default()
                .with_min_level(log::Level::Debug)
                .with_tag("RustRootOperations")
        );
    });
}

/// Log error message (Android only)
#[cfg(target_os = "android")]
fn log_error(msg: &str) {
    log::error!("{}", msg);
}

/// Log error message (non-Android platforms)
#[cfg(not(target_os = "android"))]
fn log_error(msg: &str) {
    eprintln!("ERROR: {}", msg);
}

/// JNI function to check if a path is a directory
/// This replaces the C function: Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_isDirectory
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_amaze_filemanager_fileoperations_filesystem_root_NativeOperations_isDirectory(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jboolean {
    #[cfg(target_os = "android")]
    init_logging();
    
    // Convert the JString to a Rust string
    let path_str = match env.get_string(&path) {
        Ok(java_str) => java_str,
        Err(_) => {
            log_error("Failed to get string from JString");
            return 0; // false
        }
    };
    
    let path_string: String = path_str.into();
    
    // Check if the path is a directory using Rust's standard library
    match check_is_directory(&path_string) {
        Ok(is_dir) => {
            if is_dir { 1 } else { 0 } // true : false
        }
        Err(e) => {
            log_error(&format!("Error checking if path is directory: {}", e));
            0 // false on error
        }
    }
}

/// Helper function to check if a path is a directory
/// This replicates the logic from the original C code
pub fn check_is_directory(path: &str) -> Result<bool, Box<dyn std::error::Error>> {
    let path = Path::new(path);
    
    match path.metadata() {
        Ok(metadata) => {
            Ok(metadata.is_dir())
        }
        Err(e) => {
            use std::io::ErrorKind;
            match e.kind() {
                ErrorKind::NotFound => Ok(false),
                ErrorKind::PermissionDenied => Ok(false),
                _ => {
                    // For other errors, we'll return false similar to the C code
                    // The original C code had special handling for ELOOP (too many symbolic links)
                    // which would return true, but Rust's std::fs handles this differently
                    Ok(false)
                }
            }
        }
    }
}

/// Helper function to check if a path exists
/// This can be used for future extensions
pub fn check_path_exists(path: &str) -> Result<bool, Box<dyn std::error::Error>> {
    let path = Path::new(path);
    Ok(path.exists())
}

/// Helper function to check if a path is a file
/// This can be used for future extensions
pub fn check_is_file(path: &str) -> Result<bool, Box<dyn std::error::Error>> {
    let path = Path::new(path);
    
    match path.metadata() {
        Ok(metadata) => {
            Ok(metadata.is_file())
        }
        Err(e) => {
            use std::io::ErrorKind;
            match e.kind() {
                ErrorKind::NotFound => Ok(false),
                ErrorKind::PermissionDenied => Ok(false),
                _ => Ok(false)
            }
        }
    }
}

/// Helper function to get file size
/// This can be used for future extensions
pub fn get_file_size(path: &str) -> Result<u64, Box<dyn std::error::Error>> {
    let path = Path::new(path);
    
    match path.metadata() {
        Ok(metadata) => {
            Ok(metadata.len())
        }
        Err(e) => {
            Err(Box::new(e))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_check_is_directory() {
        // Test with a known directory
        assert!(check_is_directory("/tmp").unwrap_or(false));
        
        // Test with a non-existent path
        assert!(!check_is_directory("/this/path/does/not/exist").unwrap_or(true));
        
        // Test with root directory
        assert!(check_is_directory("/").unwrap_or(false));
    }
    
    #[test]
    fn test_check_path_exists() {
        // Test with root directory (should exist)
        assert!(check_path_exists("/").unwrap_or(false));
        
        // Test with non-existent path
        assert!(!check_path_exists("/this/path/does/not/exist").unwrap_or(true));
    }
    
    #[test]
    fn test_check_is_file() {
        // Test with a directory (should return false)
        assert!(!check_is_file("/").unwrap_or(true));
        
        // Test with non-existent path
        assert!(!check_is_file("/this/path/does/not/exist").unwrap_or(true));
    }
} 