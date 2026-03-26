// Main library file for the Rust file operations module
// This file organizes and re-exports functionality from various modules

// Module declarations
pub mod rootoperations;

// Re-export commonly used functions for easier access
pub use rootoperations::{
    check_is_directory,
    check_path_exists,
    check_is_file,
    get_file_size,
};

// The JNI functions are automatically exported due to #[no_mangle] in their respective modules
// No need to re-export them here as they're called directly by the JVM

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_module_integration() {
        // Test that we can access rootoperations functions through re-exports
        assert!(check_path_exists("/").unwrap_or(false));
        assert!(check_is_directory("/").unwrap_or(false));
        assert!(!check_is_file("/").unwrap_or(true));
    }
} 