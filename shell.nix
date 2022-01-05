{ jdk ? "jdk11" }:

let

  nixpkgs = builtins.fetchTarball {
    name   = "nixos-unstable-2021-12-27";
    url    = "https://github.com/NixOS/nixpkgs/archive/be5272250926e352427b3c62c6066a95c6592375.tar.gz";
    sha256 = "0rda00l8rdf0a4pdsflg0h7dx6hd52291ymcqv1wljzs9k5zsy7i";
  };

  config = import ./config.nix { inherit jdk; };
  pkgs      = import nixpkgs { inherit config; };
  stdenv    = pkgs.stdenv;
  optionals = pkgs.lib.optionals;

  buildIstioCtl =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "istioctl";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          tar xvf $src -C $out/bin/
          chmod +x $out/bin/istioctl
        '';
      };

  buildKbld =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "kbld";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          cp $src $out/bin/kbld
          chmod +x $out/bin/kbld
        '';
      };

  buildKapp =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "kapp";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          cp $src $out/bin/kapp
          chmod +x $out/bin/kapp
        '';
      };

  buildKubeone =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "kubeone";
        src = package;
        phases = [ "installPhase" ];
        nativeBuildInputs = [ pkgs.unzip ];
        installPhase = ''
          mkdir -p $out/bin
          unzip -p $src kubeone > $out/bin/kubeone
          chmod +x $out/bin/kubeone
        '';
      };


  buildPomeriumCli =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "pomerium-cli";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          tar xvf $src -C $out/bin/
          chmod +x $out/bin/pomerium-cli
        '';
      };

  buildVelero =
    { platform,
      version,
      package
    }:
      pkgs.stdenv.mkDerivation {
        name = "velero";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          tar xvf $src velero-${version}-${platform}-amd64/velero -C $out/bin/
          mv velero-${version}-${platform}-amd64/velero $out/bin/velero
          chmod +x $out/bin/velero
        '';
      };

  buildPulsarCtl =
    { platform
    , package
    }:
      pkgs.stdenv.mkDerivation {
        name   = "pulsarctl";
        src    = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          tar xvf $src pulsarctl-amd64-${platform}/pulsarctl -C $out/bin/
          mv pulsarctl-amd64-${platform}/pulsarctl $out/bin/pulsarctl
          chmod +x $out/bin/pulsarctl
        '';
      };

  buildVirtctl =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "virtctl";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          cp $src $out/bin/virtctl
          chmod +x $out/bin/virtctl
        '';
      };

  buildPulumi =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "pulumi";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          tar xvf $src -C $out/bin/
          mv $out/bin/pulumi $out/bin/pulumi-tmp
          mv $out/bin/pulumi-tmp/* $out/bin/
          rm -r $out/bin/pulumi-tmp
          chmod +x $out/bin/pulumi
        '';
      };
  buildPulumictl =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "pulumictl";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          tar xvf $src -C $out/bin/
          chmod +x $out/bin/pulumictl
        '';
      };


  buildCRD2Pulumi =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "crd2pulumi";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          tar xvf $src -C $out/bin/
          chmod +x $out/bin/crd2pulumi
        '';
      };

  buildVcluster =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "vcluster";
        src = package;
        phases = [ "installPhase" ];
        installPhase = ''
          mkdir -p $out/bin
          cp $src $out/bin/vcluster
          chmod +x $out/bin/vcluster
        '';
      };

  buildKubelogin =
    { package
    }:
      pkgs.stdenv.mkDerivation {
        name = "kubelogin";
        src = package;
        phases = [ "installPhase" ];
        nativeBuildInputs = [ pkgs.unzip ];
        installPhase = ''
          mkdir -p $out/bin
          unzip -p $src kubelogin > $out/bin/oidc-login
          chmod +x $out/bin/oidc-login
        '';
      };

  istioctl-linux = buildIstioCtl {
    package = pkgs.fetchurl {
      url    = "https://github.com/istio/istio/releases/download/1.11.2/istioctl-1.11.2-linux-amd64.tar.gz";
      sha256 = "0j8p7v2pyfsb6bxlf840axvprclncq5raslvb09ddgiz99a2f5ir";
    };
  };

  istioctl-darwin = buildIstioCtl {
    package = pkgs.fetchurl {
      url    = "https://github.com/istio/istio/releases/download/1.11.2/istioctl-1.11.2-osx.tar.gz";
      sha256 = "1fa51q0jp2rr726m05q3dcspswdyc4qa4s0lk5wllx2hls0gpjsy";
    };
  };

  kbld-linux = buildKbld {
    package = pkgs.fetchurl {
      url    = "https://github.com/vmware-tanzu/carvel-kbld/releases/download/v0.30.0/kbld-linux-amd64";
      sha256 = "0350i6484migcqwb2kpzx46gf3ipdh3jxbn3nib542d9wxrcbibn";
    };
  };

  kbld-darwin = buildKbld {
    package = pkgs.fetchurl {
      url    = "https://github.com/vmware-tanzu/carvel-kbld/releases/download/v0.30.0/kbld-darwin-amd64";
      sha256 = "1i6ydd63vwp5dm90rn9zyv1yi4l15h7q4gs6gj4pv0y2n014s9vk";
    };
  };

  kapp-linux = buildKapp {
    package = pkgs.fetchurl {
      url    = "https://github.com/vmware-tanzu/carvel-kapp/releases/download/v0.40.0/kapp-linux-amd64";
      sha256 = "111ryq9fkgz8zv4nnaa29n8p2kk86ja5cb2cijk23idflgwdrkwl";
    };
  };

  kapp-darwin = buildKapp {
    package = pkgs.fetchurl {
      url    = "https://github.com/vmware-tanzu/carvel-kapp/releases/download/v0.40.0/kapp-darwin-amd64";
      sha256 = "03842mf278blbnabgic5y64pr7sn0c9g6n9vfrsdy87b0p6waryn";
    };
  };

  pomerium-cli-linux = buildPomeriumCli {
    package = pkgs.fetchurl {
      url    = "https://github.com/pomerium/pomerium/releases/download/v0.15.2/pomerium-cli-linux-amd64.tar.gz";
      sha256 = "0kjqbfpii8h770f0zqk8765fbrc37hkriw4m46b4akqfymhc35mb";
    };
  };

  pomerium-cli-darwin = buildPomeriumCli {
    package = pkgs.fetchurl {
      url    = "https://github.com/pomerium/pomerium/releases/download/v0.15.2/pomerium-cli-darwin-amd64.tar.gz";
      sha256 = "10zhhn41sik7l042l105znqgq6jq2y8h90998pw59yxhj462qx4h";
    };
  };

  velero-linux = buildVelero {
    platform = "linux";
    version  = "v1.6.3";
    package = pkgs.fetchurl {
      url    = "https://github.com/vmware-tanzu/velero/releases/download/v1.6.3/velero-v1.6.3-linux-amd64.tar.gz";
      sha256 = "1jh9czxifc8cg6z522m5grkqyn4mvn6yrmmpnz2bzlwjaybhj182";
    };
  };

  velero-darwin = buildVelero {
    platform = "darwin";
    version  = "v1.6.3";
    package = pkgs.fetchurl {
      url    = "https://github.com/vmware-tanzu/velero/releases/download/v1.6.3/velero-v1.6.3-darwin-amd64.tar.gz";
      sha256 = "13k6xrkg4m13iybvpp4212jck1ij2gdsxjgqsy6fg18i5yb9kgm3";
    };
  };

  pulsarctl-linux = buildPulsarCtl {
    platform = "linux";
    package  = pkgs.fetchurl {
      url    = "https://github.com/streamnative/pulsarctl/releases/download/v2.8.1.0/pulsarctl-amd64-linux.tar.gz";
      sha256 = "0v1qzmpsvkg491v75mj3s7l7bc7nbph9ip36646icz5q971id2jw";
    };
  };

  pulsarctl-darwin = buildPulsarCtl {
    platform = "darwin";
    package  = pkgs.fetchurl {
      url    = "https://github.com/streamnative/pulsarctl/releases/download/v2.8.1.0/pulsarctl-amd64-darwin.tar.gz";
      sha256 = "00w43m0w6hyp5pd9s0zcjn9vg385pw8fmvfpjl7q8mz6vwnyvx49";
    };
  };

  pulumi-linux = buildPulumi {
    package = pkgs.fetchurl {
      url    = "https://github.com/pulumi/pulumi/releases/download/v3.21.0/pulumi-v3.21.0-linux-x64.tar.gz";
      sha256 = "1w4xgjknvsnvzgj7c5wcbp8716k8lixz37bamqmj78y5z9lzhf37";
    };
  };

  pulumi-darwin = buildPulumi {
    package = pkgs.fetchurl {
      url    = "https://github.com/pulumi/pulumi/releases/download/v3.21.0/pulumi-v3.21.0-darwin-x64.tar.gz";
      sha256 = "131aafni3yhv65i3zjjlwnr94g65h3v2pplblx2cnw8w5160f2p5";
    };
  };

  pulumictl-linux = buildPulumictl {
    package = pkgs.fetchurl {
      url    = "https://github.com/pulumi/pulumictl/releases/download/v0.0.29/pulumictl-v0.0.29-linux-amd64.tar.gz";
      sha256 = "02i6jx0vrfrhx9ml4rp9ib9lh0hi5l7zssykbz86l9bxdb3xnp85";
    };
  };

  pulumictl-darwin = buildPulumictl {
    package = pkgs.fetchurl {
      url    = "https://github.com/pulumi/pulumictl/releases/download/v0.0.29/pulumictl-v0.0.29-darwin-amd64.tar.gz";
      sha256 = "1pbx8gb1pqm21y3qr29z4byj2lszcgrqhifikss0pvkjnmhsm5a3";
    };
  };


  crd2pulumi-linux = buildCRD2Pulumi {
    package = pkgs.fetchurl {
      url    = "https://github.com/pulumi/crd2pulumi/releases/download/v1.0.8/crd2pulumi-v1.0.8-linux-amd64.tar.gz";
      sha256 = "04mpb5kvggrfamvabbr0d4wvh37pjblqrmg0r641nx286ac7idr1";
    };
  };

  crd2pulumi-darwin = buildCRD2Pulumi {
    package = pkgs.fetchurl {
      url    = "https://github.com/pulumi/crd2pulumi/releases/download/v1.0.8/crd2pulumi-v1.0.8-darwin-amd64.tar.gz";
      sha256 = "00f8nz8ndgnkysrwxrp0plmx3q0693vsvcsmd2ld8bzn9qlsyrp8";
    };
  };

  vcluster-linux = buildVcluster {
    package = pkgs.fetchurl {
      url    = "https://github.com/loft-sh/vcluster/releases/download/v0.4.0/vcluster-linux-amd64";
      sha256 = "10m0hl3rx5kvjqn7779pdhzlkxfs7p4kws6djkr18ra3b7j351ql";
    };
  };

  vcluster-darwin = buildVcluster {
    package = pkgs.fetchurl {
      url    = "https://github.com/loft-sh/vcluster/releases/download/v0.4.0/vcluster-darwin-amd64";
      sha256 = "1qv61qn6ix9gbwg4skqh6ik33mjfzhxdpcashknl69bh1fbw7hm3";
    };
  };

  kubelogin-linux = buildKubelogin {
    package = pkgs.fetchurl {
      url    = "https://github.com/int128/kubelogin/releases/download/v1.25.0/kubelogin_linux_amd64.zip";
      sha256 = "1an6sdlmd00m0fspsdm6m9ijgr2xh41p7389mwiqzk263zvmmalm";
    };
  };

  kubelogin-darwin = buildKubelogin {
    package = pkgs.fetchurl {
      url    = "https://github.com/int128/kubelogin/releases/download/v1.25.0/kubelogin_darwin_amd64.zip";
      sha256 = "18s26pshjb5mjdvarl6cym36ma6pz413g4sjjb5wayg33r28lbx8";
    };
  };

  commonPkgs = with pkgs; [
      ammonite_2_13   # 2.3.8
      curl            # 7.74.0
      git             # 2.30.1
      jdk11           # 11.0.10
      kind            # 0.10.0
      kubectl         # 1.20.4
      nodejs-16_x     # 16.3.0
      yarn            # 1.22.10
      pre-commit      # 2.11.0
      sops            # 3.6.1
      python3         # 3.8.8
      docker-compose  # 1.28.5
      k9s             # 0.24.2
      sbt             # 1.4.9
      minio-client    # RELEASE.2021-03-12T03-36-59Z
      kubeseal        # 0.16.0      
      kubie           # 0.9.1
      pwgen           # 2.08
      flyway          # 7.5.4
      ansible         # 2.10.0
      ansible-lint    # 4.3.7
      packer          # 1.7.0
      kubernetes-helm # 3.5.3
      kustomize       # 4.1.3
      unzip
      python38Packages.pip
      python38Packages.virtualenv
    ];

  darwinPkgs = with pkgs; [

    pulumi-darwin

  ];

  linuxPkgs = with pkgs; [

    pulumi-linux

  ];

  packages = commonPkgs ++ optionals stdenv.isDarwin darwinPkgs ++ optionals stdenv.isLinux linuxPkgs;
in

  pkgs.mkShell {
    buildInputs = packages;

    shellHook = ''
    '';
  }
