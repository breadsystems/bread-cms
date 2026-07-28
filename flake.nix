{
  description = "The Bread development environment";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
      };
      # Minimal toolchain needed to compile the native binary.
      buildPackages = with pkgs; [
        clojure
        graalvmPackages.graalvm-ce
      ];
      lintPackages = with pkgs; [
        clj-kondo
      ];
    in
    {
      devShells."${system}" = {
        # Full development environment.
        default = pkgs.mkShell {
          packages = buildPackages ++ lintPackages ++ (with pkgs; [
            babashka
            nodejs_22
            yarn-berry
            zulu17
          ]);
        };
        # Lean shell for building the binary in CI.
        build = pkgs.mkShell {
          packages = buildPackages;
        };
        # Ditto for linting.
        lint = pkgs.mkShell {
          packages = lintPackages;
        };
      };
    };
}
