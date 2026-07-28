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
      jdk = pkgs.jdk21;
      clojure = pkgs.clojure.override { inherit jdk; };

      lintPackages = with pkgs; [ clj-kondo ];
      cljPackages = with pkgs; [ clojure ];
      buildPackages = with pkgs; [ clojure graalvmPackages.graalvm-ce ];
      devPackages = with pkgs; [
        babashka
        nodejs_22
        yarn-berry
      ];
    in
    {
      devShells."${system}" = {
        # Lean shells for running clojure, lint, binary builds in isolation.
        clj = pkgs.mkShell { packages = cljPackages; };
        build = pkgs.mkShell { packages = buildPackages; };
        lint = pkgs.mkShell { packages = lintPackages; };
        # Full development environment.
        default = pkgs.mkShell {
          packages = cljPackages ++ buildPackages ++ lintPackages ++ devPackages;
        };
      };
    };
}
