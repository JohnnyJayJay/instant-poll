{
  inputs = {
    flake-utils.url = "github:numtide/flake-utils/v1.0.0";
    nixpkgs.url = "github:NixOS/nixpkgs/24.05";
    clj-nix.url = "github:jlesquembre/clj-nix";
  };

  outputs = { self, flake-utils, clj-nix, nixpkgs, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      {
        packages = rec {
          default = instant-poll;
          instant-poll = clj-nix.lib.mkCljApp {
            pkgs = nixpkgs.legacyPackages.${system};
            modules = [
              {
                projectSrc = ./.;
                name = "instant-poll";
                # This must be the same as `:main` in project.clj
                main-ns = "instant-poll.handler";
                buildCommand = "lein uberjar";
                withLeiningen = true;
              }
            ];
          };
        };
      }
    );
}
