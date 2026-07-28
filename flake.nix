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
      cljPackages = with pkgs; [
        clojure
      ];
      buildPackages = with pkgs; [
        clojure
        graalvmPackages.graalvm-ce
      ];
      lintPackages = with pkgs; [
        clj-kondo
      ];
      devPackages = with pkgs; [
        babashka
        nodejs_22
        yarn-berry
        zulu17
      ];
    in
    {
      devShells."${system}" = {
        # Lean shell for running clojure commands in isolation.
        clj = pkgs.mkShell {
          packages = cljPackages;
        };
        # Shell for building the binary in CI.
        build = pkgs.mkShell {
          packages = buildPackages;
        };
        # Ditto for linting.
        lint = pkgs.mkShell {
          packages = lintPackages;
        };
        # Full development environment.
        default = pkgs.mkShell {
          packages = cljPackages ++ buildPackages ++ lintPackages ++ devPackages;
        };
      };
    };
}
