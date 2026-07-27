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
    in
    {
      devShells."${system}" = {
        # Full development environment.
        default = pkgs.mkShell {
          packages = buildPackages ++ (with pkgs; [
            nodejs_22
            zulu17
            babashka
            yarn-berry
          ]);
        };
        # Lean shell for building the binary in CI. Excludes the JS toolchain
        # (nodejs/yarn) and extra JDK so the /nix/store closure that CI caches
        # stays small.
        build = pkgs.mkShell {
          packages = buildPackages;
        };
      };
    };
}
