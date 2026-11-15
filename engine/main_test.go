package main

import (
	"encoding/json"
	"testing"

	"github.com/heartleo/zlib"
)

func TestInitAndStatus(t *testing.T) {
	tmp := t.TempDir()
	payload, _ := json.Marshal(map[string]string{"dataDir": tmp})
	res := dispatch("init", payload)

	var out map[string]any
	if err := json.Unmarshal(res, &out); err != nil {
		t.Fatalf("unmarshal init response: %v", err)
	}
	if out["ok"] != true {
		t.Fatalf("expected ok=true, got %v", out)
	}
	if out["loggedIn"] != false {
		t.Fatalf("expected loggedIn=false, got %v", out["loggedIn"])
	}
}

func TestParseCookiesInput(t *testing.T) {
	// JSON format
	c1, err := parseCookiesInput(`{"remix_userid":"123","remix_userkey":"abc"}`, "https://z-lib.gd")
	if err != nil {
		t.Fatalf("json parse failed: %v", err)
	}
	if c1["remix_userid"] != "123" || c1["remix_userkey"] != "abc" {
		t.Fatalf("unexpected cookies: %v", c1)
	}

	// Semicolon format
	c2, err := parseCookiesInput(`remix_userid=456; remix_userkey=xyz`, "https://z-lib.gd")
	if err != nil {
		t.Fatalf("kv parse failed: %v", err)
	}
	if c2["remix_userid"] != "456" || c2["remix_userkey"] != "xyz" {
		t.Fatalf("unexpected cookies: %v", c2)
	}

	// Netscape format
	netscape := "# Netscape HTTP Cookie File\n.z-lib.gd\tTRUE\t/\tTRUE\t0\tremix_userid\t789\n.z-lib.gd\tTRUE\t/\tTRUE\t0\tremix_userkey\tkey999\n"
	c3, err := parseCookiesInput(netscape, "https://z-lib.gd")
	if err != nil {
		t.Fatalf("netscape parse failed: %v", err)
	}
	if c3["remix_userid"] != "789" || c3["remix_userkey"] != "key999" {
		t.Fatalf("unexpected netscape cookies: %v", c3)
	}
}

func TestBookCommandIDAndCover(t *testing.T) {
	bEAPI := zlib.Book{ID: "12345", Hash: "abcdef"}
	if got := bookCommandID(bEAPI); got != "12345:abcdef" {
		t.Fatalf("expected 12345:abcdef, got %q", got)
	}

	bHTML := zlib.Book{URL: "https://z-lib.gd/book/OarG6e3pPD/norwegian-wood.html"}
	if got := bookCommandID(bHTML); got != "OarG6e3pPD" {
		t.Fatalf("expected OarG6e3pPD, got %q", got)
	}

	if got := absolutizeCover("https://z-lib.gd", "/covers/a.jpg"); got != "https://z-lib.gd/covers/a.jpg" {
		t.Fatalf("unexpected cover url: %q", got)
	}
}
