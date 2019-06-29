class C {
  void defaultBranchHasNoResult(int n) {
    String s = <error descr="Switch expression should produce result in all execution paths">switch</error> (n) {
      default:
    };
  }

  void defaultRuleHasNoResult(int n) {
    String s = switch (n) {
      <error descr="Switch expression rule should produce result in all execution paths">default</error> -> {}
    };
  }

  void defaultBranchSometimesHasNoResult(int n, boolean b) {
    String s = <error descr="Switch expression should produce result in all execution paths">switch</error> (n) {
      default: {
        if (b) break "";
      }
    };
  }

  void defaultRuleSometimesHasNoResult(int n, boolean b) {
    String s = switch (n) {
      <error descr="Switch expression rule should produce result in all execution paths">default</error> -> {
        if (b) break "";
      }
    };
  }

  void defaultBranchAlwaysThrows(int n) {
    String s = switch (n) {
      default: throw new RuntimeException();
    };
  }

  void defaultRuleAlwaysThrows(int n) {
    String s = switch (n) {
      default -> throw new RuntimeException();
    };
  }

  void defaultBranchSometimesThrows(int n, boolean b) {
    String s = switch (n) {
      default:
        if (b) throw new RuntimeException();
        break "";
    };
  }

  void defaultRuleSometimesThrows(int n, boolean b) {
    String s = switch (n) {
      default -> {
        if (b) throw new RuntimeException();
        break "";
      }
    };
  }

  void defaultBranchHasManyResults(int n, int k) {
    String s = switch (n) {
      default: {
        if (k < n) break "a";
        if (k > n) break "b";
        break "c";
      }
    };
  }

  void defaultRuleHasManyResults(int n, int k) {
    String s = switch (n) {
      default -> {
        if (k < n) break "a";
        if (k > n) break "b";
        break "c";
      }
    };
  }

  void oneOfBranchesHasNoResult(int n) {
    String s = <error descr="Switch expression should produce result in all execution paths">switch</error> (n) {
      case 0: break "";
      default:
    };
  }

  void oneOfRulesHasNoResult(int n) {
    String s = switch (n) {
      case 0 -> "";
      <error descr="Switch expression rule should produce result in all execution paths">default</error> -> {
      }
    };
  }

  void allBranchesHaveNoResult(int n) {
    String s = <error descr="Switch expression should produce result in all execution paths">switch</error> (n) {
      case 0:
      default:
    };
  }

  void allRulesHaveNoResult(int n) {
    String s = switch (n) {
      <error descr="Switch expression rule should produce result in all execution paths">case</error> 0 -> {
      }
      <error descr="Switch expression rule should produce result in all execution paths">default</error> -> {
      }
    };
  }

  void allBranchesDoHaveResult(int n) {
    String s = switch (n) {
      case -1: throw new RuntimeException();
      case 0: break "a";
      default: break "b";
    };
  }

  void allRulesDoHaveResult(int n) {
    String s = switch (n) {
      case -1 -> throw new RuntimeException();
      case 0 -> "a";
      default -> "b";
    };
  }

  void allBranchesDoHaveResultInFinally(int n) {
    String s;
    try {
    } finally {
      s = switch (n) {
        case -1: throw new RuntimeException();
        case 0: break "a";
        default: break "b";
      };
    }
  }

  void allRulesDoHaveResultInFinally(int n) {
    String s;
    try {
    } finally {
      s = switch (n) {
        case -1 -> throw new RuntimeException();
        case 0 -> "a";
        default -> "b";
      };
    }
  }
}