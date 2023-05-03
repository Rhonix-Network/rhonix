let

  pkgs = import <nixpkgs> {};

  jdk = pkgs.openjdk8;

  sbt = pkgs.sbt.override { jre = jdk.jre; };

in rec {

  rhonixEnv = pkgs.buildFHSUserEnv {
    name = "rhonix";
    targetPkgs = ps: rhonixLabsPackages;
  };

  rhonixLabsPackages = with pkgs; [ haskellPackages.BNFC git jflex sbt jdk ];
}
